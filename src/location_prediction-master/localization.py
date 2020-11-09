import mysql.connector as myc
import numpy as np
from datetime import datetime, timedelta


def read_local_data(start_date, end_date, table, simu=False, user=''):
	config = dict()
	with open('log_info.txt') as f:
		info = f.readlines()
		# config["host"] = "sensoria-mysql.ics.uci.edu" //use log_info_server.txt
		config["host"] = "localhost"
		config['user'] = info[0].strip()
		config['password'] = info[1].strip()
		config['database'] = info[2].strip()
		config['auth_plugin'] = 'mysql_native_password'
	cnx = myc.connect(**config)
	cursor = cnx.cursor()
	if simu:
		query = f'SELECT timestamp, sensor_id FROM {table} WHERE timestamp >={start_date} AND date(timestamp)<={end_date} AND user={user} ORDER BY timestamp'
	else:
		query = 'SELECT timestamp, sensor_id FROM %s WHERE timestamp >= %s AND date(timestamp) <= %s ORDER BY timestamp' % (
			table, start_date, end_date)
	cursor.execute(query)
	results = cursor.fetchall()
	cnx.close()
	return results


def get_server_connection():
	config = dict()
	with open('log_info_server.txt') as f:
		info = f.readlines()
		config["host"] = "sensoria-mysql.ics.uci.edu"
		config['user'] = info[0].strip()
		config['password'] = info[1].strip()
		config['database'] = 'tippersdb_restored'
	cnx = myc.connect(**config)
	return cnx


class Interval:
	def __init__(self, st, ed, st_ap, ed_ap):
		self.date = st.date()
		self.start_time = st.time()
		self.end_time = ed.time()
		self.start_datetime = st
		self.end_datetime = ed
		self.start_ap = st_ap
		self.end_ap = ed_ap
		if st_ap == ed_ap:
			self.same_ap = 1
		else:
			self.same_ap = 0
		self.duration = (ed - st).total_seconds() / 60


def create_intervals(cnx_entries):
	result = list()
	ap_set = set()
	ap_frequency = dict()
	connection_count = np.zeros(1440)
	if len(cnx_entries) < 2:
		return result, list(ap_set), connection_count
	num_of_day = 1
	last_num = 0
	last_date = 0
	for i in range(len(cnx_entries) - 1):
		nxt_start = cnx_entries[i]
		start_ap = nxt_start[1]
		ap_set.add(start_ap)
		ap_frequency[start_ap] = ap_frequency.get(start_ap, 0) + 1
		nxt_end = cnx_entries[i + 1]
		if nxt_start[0].date() == nxt_end[0].date():
			result.append(Interval(nxt_start[0], nxt_end[0], nxt_start[1], nxt_end[1]))
		else:
			num_of_day += 1
		t = get_minute_in_day(nxt_start[0])
		if t != last_num or last_date != nxt_start[0].date():
			connection_count[t] += 1
			last_num = t
			last_date = nxt_start[0].date()

	ap_set.add(cnx_entries[-1][1])
	ap_frequency[cnx_entries[-1][1]] = ap_frequency.get(cnx_entries[-1][1], 0) + 1
	connection_count = connection_count / num_of_day
	return result, list(ap_set), connection_count, ap_frequency


def create_gaps(cnx_entries, validity):
	# unit of validity: second
	result = list()
	connection_count = np.zeros(1440)
	ap_connection_count = dict()
	n = len(cnx_entries)
	if n < 2:
		return result, connection_count, ap_connection_count
	num_of_day = 1
	for i in range(n):
		cur_entry = cnx_entries[i]

		# update statistics
		connection_count[get_minute_in_day(cur_entry[0])] += 1
		ap_count = ap_connection_count.get(cur_entry[1], np.zeros(1440))
		ap_count[get_minute_in_day(cur_entry[0])] += 1
		ap_connection_count[cur_entry[1]] = ap_count

		if i == n - 1:  # Return since there is no next entries
			connection_count = connection_count / num_of_day
			return result, connection_count, ap_connection_count

		next_entry = cnx_entries[i + 1]
		# A gap is generated if and only if two events are far enough and still in the same day
		if next_entry[0].date() == cur_entry[0].date():
			if (next_entry[0] - cur_entry[0]).total_seconds() > 2 * validity:
				# A gap is generated, still use interval class to represent a gap
				delta = timedelta(seconds=validity)
				gap_start = cur_entry[0] + delta
				gap_end = next_entry[0] - delta
				result.append(Interval(gap_start, gap_end, cur_entry[1], next_entry[1]))
		else:
			num_of_day += 1


class Data:
	pass


def convert_interval_into_features(interval, ap_list, cnx_density):
	x = list()
	x.append(interval.duration)
	x.append(interval.same_ap)

	# connection density
	start_minute = get_minute_in_day(interval.start_time)
	end_minute = get_minute_in_day(interval.end_time)
	x.append(np.mean(cnx_density[start_minute: end_minute + 1]))
	x.append(np.sum(cnx_density[start_minute: end_minute + 1]))

	# scalar for start time, end time only 66%

	# one-hot encoder for start, end time
	x.extend(one_hot_encoder(start_minute // 60, list(range(9, 20))))
	x.extend(one_hot_encoder(end_minute // 60, list(range(9, 20))))

	# start time ap feature
	x.extend(one_hot_encoder(interval.start_ap, ap_list))

	# weekday feature
	x.extend(one_hot_encoder(interval.date.weekday(), list(range(7))))

	return x


def create_training_data(intervals, ap_list, pos_th, neg_th, cnx_density):
	# The training data is for building level localization classifier
	data = Data()
	all_x = list()
	for interval in intervals:
		x = convert_interval_into_features(interval, ap_list, cnx_density)
		all_x.append(x)
	all_x = np.array(all_x)
	data.train_x = all_x[(all_x[:, 0] <= pos_th) | (all_x[:, 0] >= neg_th)]
	data.train_y = np.zeros(data.train_x.shape[0])
	data.train_y = np.where(data.train_x[:, 0] > pos_th, data.train_y, 1)
	if np.sum(data.train_y == 0) == 0:
		# Introduce at least 5 negative examples
		durations = data.train_x[:, 0]
		indices = durations.argsort()[::-1][:5]
		data.train_y[indices] = 0
	data.other_x = all_x[(all_x[:, 0] > pos_th) & (all_x[:, 0] < neg_th)]
	data.ap_list = ap_list
	return data


def create_region_train_data(intervals, ap_list, pos_th, neg_th, cnx_density, freq_th, ap_cnx_count):
	data = Data()
	train_x = list()
	train_y = list()
	other_x = list()
	for interval in intervals:
		x = convert_interval_into_features(interval, ap_list, cnx_density)
		if interval.duration <= pos_th and interval.same_ap == 1:
			# This is rule 1
			train_x.append(x)
			train_y.append(interval.start_ap)
		elif interval.duration >= neg_th:
			# Rule 2, first compute the percentage of the most frequent WiFi AP
			duration_count_ap = dict()
			start_minute = get_minute_in_day(interval.start_time)
			end_minute = get_minute_in_day(interval.end_time)
			total_count = 0
			max_count = 0
			max_ap = 0
			for k in ap_cnx_count.keys():
				cur = np.sum(ap_cnx_count[k][start_minute:end_minute + 1])
				duration_count_ap[k] = cur
				total_count += cur
				if cur > max_count:
					max_count = cur
					max_ap = k
			# compare this percentage with the threshold
			if max_count / total_count >= freq_th:
				train_x.append(x)
				train_y.append(max_ap)
			else:
				other_x.append(x)
		else:
			other_x.append(x)
	data.train_x = np.array(train_x)
	data.train_y = np.array(train_y)
	data.other_x = np.array(other_x)
	data.ap_list = ap_list
	return data


def semi_supervised_learning(data, step_size):
	from sklearn.linear_model import LogisticRegression as LR
	clf = LR(solver='liblinear')
	clf.fit(data.train_x, data.train_y)
	while 1:
		if data.other_x.shape[0] <= step_size:
			data.train_x = np.concatenate((data.train_x, data.other_x), axis=0)
			labels = clf.predict(data.other_x)
			data.train_y = np.concatenate((data.train_y, labels), axis=0)
			clf.fit(data.train_x, data.train_y)
			break
		scores = clf.predict_proba(data.other_x)
		scores = np.abs(scores[:, 0] - scores[:, 1])
		indices = scores.argsort()[::-1][:step_size]
		data.train_x = np.concatenate((data.train_x, data.other_x[indices]), axis=0)
		labels = clf.predict(data.other_x)
		data.train_y = np.concatenate((data.train_y, labels[indices]), axis=0)
		clf.fit(data.train_x, data.train_y)
		data.other_x = np.delete(data.other_x, indices, 0)
	return clf


def semi_supervised_learning_new(data, step_size):
	# New classifier should also be able to handle multi-class prediction
	from sklearn.linear_model import LogisticRegression as LR
	clf = LR(solver='lbfgs', multi_class='multinomial')
	clf.fit(data.train_x, data.train_y)
	while 1:
		if data.other_x.shape[0] <= step_size:
			data.train_x = np.concatenate((data.train_x, data.other_x), axis=0)
			labels = clf.predict(data.other_x)
			data.train_y = np.concatenate((data.train_y, labels), axis=0)
			clf.fit(data.train_x, data.train_y)
			break
		scores = clf.predict_proba(data.other_x)
		confidences = np.std(scores, axis=1)
		indices = confidences.argsort()[::-1][:step_size]
		data.train_x = np.concatenate((data.train_x, data.other_x[indices]), axis=0)
		labels = clf.predict(data.other_x)
		data.train_y = np.concatenate((data.train_y, labels[indices]), axis=0)
		clf.fit(data.train_x, data.train_y)
		data.other_x = np.delete(data.other_x, indices, 0)
	return clf


def find_interval(query_time, all_intervals):
	if query_time < all_intervals[0].start_datetime or query_time > all_intervals[-1].end_datetime:
		return None
	if query_time >= all_intervals[-1].start_datetime:
		return all_intervals[-1]
	left = 0
	right = len(all_intervals) - 1
	while left < right:
		mid = (left + right + 1) // 2
		if query_time < all_intervals[mid].start_datetime:
			right = mid - 1
		else:
			left = mid
	return all_intervals[left]


def online_or_not(query_time, all_events, validity, name):
	# Return 0 means outside, 1 means inside and the gap if further analysis is required.
	# The second string means the access point if 1 is returned.
	n = len(all_events)
	if query_time <= all_events[0][0] or query_time >= all_events[n - 1][0]:
		if query_time <= all_events[0][0] and (all_events[0][0] - query_time).total_seconds() <= validity:
			return 1, all_events[0][1]
		if query_time >= all_events[n - 1][0] and (query_time - all_events[n - 1][0]).total_seconds() <= validity:
			return 1, all_events[n - 1][1]
		print(f'Query time {query_time} for {name} is outside the first event {all_events[0]} and the last event {all_events[-1]}')
		return 0, ''

	# compute p as the index of predecessor event to query_time
	left = 0
	right = n - 1
	while left < right:
		mid = (left + right + 1) // 2
		if query_time < all_events[mid][0]:
			right = mid - 1
		else:
			left = mid
	p = left

	# online or not decision based on validity
	d1 = (query_time - all_events[p][0]).total_seconds()
	d2 = (all_events[p + 1][0] - query_time).total_seconds()
	if d1 <= d2:
		if d1 <= validity:
			return 1, all_events[p][1]
	else:
		if d2 <= validity:
			return 1, all_events[p + 1][1]

	delta = timedelta(seconds=validity)
	gap = Interval(all_events[p][0] + delta, all_events[p + 1][0] - delta, all_events[p][1], all_events[p + 1][1])
	return gap, ''


def predict_an_interval(interval, ap_list, clf, cnx_density, ap_freq):
	x = convert_interval_into_features(interval, ap_list, cnx_density)
	x = np.array([x])
	label = clf.predict(x)[0]
	st_ap = interval.start_ap
	ed_ap = interval.end_ap
	if ap_freq.get(st_ap, 0) > ap_freq.get(ed_ap, 0):
		ap = st_ap
	else:
		ap = ed_ap
	return label, ap


def predict_a_gap(gap, bl_model, rl_clf, fast=False):
	x = convert_interval_into_features(gap, bl_model.ap_list, bl_model.density)
	x = np.array([x])
	label = bl_model.clf.predict(x)[0]
	if fast:  # The most frequent WiFi AP of start and end event
		st_ap = gap.start_ap
		ed_ap = gap.end_ap
		ap_cnx_count = bl_model.ap_freq  # ap_cnx_count is stored at ap_freq in train_bl_model
		start_minute = get_minute_in_day(gap.start_time)
		end_minute = get_minute_in_day(gap.end_time)
		st_count = np.sum(ap_cnx_count[st_ap][start_minute:end_minute + 1])
		ed_count = np.sum(ap_cnx_count[ed_ap][start_minute:end_minute + 1])
		if st_count >= ed_count:
			ap = st_ap
		else:
			ap = ed_ap
	else:
		ap = rl_clf.predict(x)[0]
	return label, ap


def get_minute_in_day(dt):
	return dt.hour * 60 + dt.minute


def one_hot_encoder(item, item_list):
	length = len(item_list)
	x = np.zeros(length)
	for i in range(length):
		if item == item_list[i]:
			x[i] = 1
	return x.tolist()


def old_test():
	entries = read_local_data('20171201', '20180815', 'roberto_20171120to20180817')
	intervals, ap_set, cnx_density, ap_freq = create_intervals(entries)
	data = create_training_data(intervals, ap_set, 15, 90, cnx_density)
	clf = semi_supervised_learning(data, 2)
	x = datetime.strptime('20171129 153049', '%Y%m%d %H%M%S')
	y = find_interval(x, intervals)
	inside, ap = predict_an_interval(y, ap_set, clf, cnx_density, ap_freq)


if __name__ == '__main__':
	entries = read_local_data('20181201', '20171202', 'roberto_20171120to20180817')
	create_gaps(entries, 300)
