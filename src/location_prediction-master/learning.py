import localization, pickle
from time import process_time
from datetime import datetime

tablenames = {
	'Abdul Alsaudi': 'abdul_20171120to20180817',
	'Dhrub': 'dhrub_20171120to20180817',
	'Roberto Yus': 'roberto_20171120to20180817',
	'EunJeong Joyce Shin': 'joyce_20171120to20180817',
	'Primal Pappachan': ''
}

start_date = '20170101'
end_date = '20171130'
pos_threshold = 30
neg_threshold = 60
pos_r_th = 20
neg_r_th = 40
freq_th = 0.25
step_size = 2
validity_second = 180


class Model:
	def __init__(self, clf, ap_list, cnx_density, ap_freq):
		self.clf = clf
		self.ap_list = ap_list
		self.density = cnx_density
		self.ap_freq = ap_freq


def train_model(user_name, table_name, start, end, pos, neg, step):
	# Train the model for a user using the data in %table_name% between %start% and %end%
	# Then store the model and return the model
	# Format of start and end like '20180309'
	clf_name = get_clf_name(user_name, pos, neg, start)
	cnxs = localization.read_local_data(start, end, table_name)
	intervals, ap_list, cnx_density, ap_freq = localization.create_intervals(cnxs)
	data = localization.create_training_data(intervals, ap_list, pos, neg, cnx_density)
	clf = localization.semi_supervised_learning(data, step)
	model = Model(clf, ap_list, cnx_density, ap_freq)
	pickle.dump(model, open(clf_name, 'wb'))
	return model


def answer_query(query_time, user_name, table_name, start=start_date, end=end_date,
				 pos=pos_threshold, neg=neg_threshold, step=step_size):
	# Check whether the model exists for a user, if not generate one, if does, load one
	# query_time: datetime type python
	date_str = str(query_time.date()).replace('-', '')
	all_entries = localization.read_local_data(date_str, date_str, table_name)
	if len(all_entries) == 0 or len(all_entries) == 1:
		print('Query %s for %s: No connection activities on that day.' % (query_time, user_name))
		return 0, ''
	intervals, _, _, _ = localization.create_intervals(all_entries)
	interval = localization.find_interval(query_time, intervals)
	if interval is None:
		print('Query %s for %s: Query time before the first connection or after the last on that day.' % (
			query_time, user_name))
		return 0, ''
	try:
		f = open(get_clf_name(user_name, pos, neg, start), 'rb')
		model = pickle.load(f)
	except FileNotFoundError:
		try:
			model = train_model(user_name, table_name, start, end, pos, neg, step)
		except ValueError:
			return 1, interval.start_ap
	state, ap = localization.predict_an_interval(interval, model.ap_list, model.clf, model.density, model.ap_freq)
	return state, ap


def train_bl_model(user_name, table_name, start, end, pos, neg, step, validity, simu=False):
	# Train the model for building level localization
	clf_name = get_bl_clf_name(user_name, pos, neg, start, end, validity)
	all_events = localization.read_local_data(start, end, table_name, simu, user_name)
	gaps, cnx_density, ap_cnx_count = localization.create_gaps(all_events, validity)
	data = localization.create_training_data(gaps, list(ap_cnx_count.keys()), pos, neg, cnx_density)
	clf = localization.semi_supervised_learning(data, step)
	model = Model(clf, list(ap_cnx_count.keys()), cnx_density, ap_cnx_count)
	pickle.dump(model, open(clf_name, 'wb'))
	return model


def train_rl_model(user_name, table_name, start, end, pos, neg, step, validity, freq, simu=False):
	# Train the model for region level localization
	clf_name = get_rl_clf_name(user_name, pos, neg, start, end, validity, freq)
	all_events = localization.read_local_data(start, end, table_name, simu, user_name)
	gaps, cnx_density, ap_cnx_count = localization.create_gaps(all_events, validity)
	data = localization.create_region_train_data(gaps, list(ap_cnx_count.keys()), pos, neg, cnx_density, freq_th,
												 ap_cnx_count)
	clf = localization.semi_supervised_learning_new(data, step)
	pickle.dump(clf, open(clf_name, 'wb'))
	return clf


def answer_query_new(query_time, user_name, table_name, start=start_date, end=end_date,
					 pos=pos_threshold, neg=neg_threshold, validity=validity_second,
					 pos_r=pos_r_th, neg_r=neg_r_th, freq=freq_th, fast=False, step=step_size, simu=False):
	# Assume the location data for a user exists and load all events in the queried day
	date_str = str(query_time.date()).replace('-', '')
	read_start = process_time()
	all_entries = localization.read_local_data(date_str, date_str, table_name, simu, user_name)
	read_end = process_time()
	print('data read time: %.2f ms' % (1000 * (read_end - read_start),))
	run_start = process_time()
	if len(all_entries) == 0 or len(all_entries) == 1:
		if len(all_entries) == 1 and all_entries[0][0] == query_time:
			return 1, all_entries[0][1]
		print('Query for %s at %s: Not enough connection activities on that day.' % (user_name, query_time))
		return 0, ''

	# First check whether online or not, if online, work is done
	state, ap = localization.online_or_not(query_time, all_entries, validity, user_name)
	if state == 0 or state == 1:
		print('Execution time: %.2f ms' % ((process_time() - run_start) * 1000,))
		return state, ap

	# Further analysis is needed. The semi-supervised learning method comes. First building level model.
	try:
		f = open(get_bl_clf_name(user_name, pos, neg, start, end, validity), 'rb')
		bl_model = pickle.load(f)
	except FileNotFoundError:
		bl_model = train_bl_model(user_name, table_name, start, end, pos, neg, step, validity)

	# Then region level model
	if fast:
		rl_clf = 0
	else:
		try:
			f = open(get_rl_clf_name(user_name, pos_r, neg_r, start, end, validity, freq), 'rb')
			rl_clf = pickle.load(f)
		except FileNotFoundError:
			rl_clf = train_rl_model(user_name, table_name, start, end, pos_r, neg_r, step, validity, freq)

	state, ap = localization.predict_a_gap(state, bl_model, rl_clf, fast)
	print('Execution time: %.2f ms' % ((process_time() - run_start) * 1000,))
	return state, ap


def answer_query_without_cache(query_mac, query_time, threshold=300):
	server_cnx = localization.get_server_connection()
	cursor = server_cnx.cursor()
	q1 = f"SELECT timestamp, sensor_id FROM OBSERVATION_CLEAN WHERE payload = '{query_mac}' and timestamp = '{query_time}'"
	cursor.execute(q1)
	r1 = cursor.fetchone()
	if r1 is not None:
		server_cnx.close()
		return 1, r1[1]
	q2 = f"SELECT max(timestamp), sensor_id FROM OBSERVATION_CLEAN WHERE payload = '{query_mac}' and timestamp < '{query_time}'"
	q3 = f"SELECT min(timestamp), sensor_id FROM OBSERVATION_CLEAN WHERE payload = '{query_mac}' and timestamp > '{query_time}'"
	cursor.execute(q2)
	r2 = cursor.fetchone()
	if r2[0] is not None:
		d1 = (query_time - r2[0]).seconds
	else:
		d1 = 100000
	cursor.execute(q3)
	r3 = cursor.fetchone()
	if r3[0] is not None:
		d2 = (r3[0] - query_time).seconds
	else:
		d2 = 100000
	if d1 < d2 and d1 <= threshold:
		server_cnx.close()
		return 1, r2[1]
	elif d1 >= d2 and d2 <= threshold:
		server_cnx.close()
		return 1, r3[1]
	server_cnx.close()
	return 0, ''


def get_clf_name(name, pos, neg, st):
	nn = name.replace(' ', '_').replace('@', '_').replace('.', '_').lower()
	return f"./models/{nn}{pos}{neg}{st}.clf"


def get_bl_clf_name(name, pos, neg, st, ed, validity):
	nn = name.replace(' ', '_').replace('@', '_').replace('.', '_').lower()
	return f"./models/{nn}p{pos}n{neg}from{st}to{ed}v{validity}.bclf"


def get_rl_clf_name(name, pos, neg, st, ed, validity, freq):
	nn = name.replace(' ', '_').replace('@', '_').replace('.', '_').lower()
	return f"./models/{nn}p{pos}n{neg}from{st}to{ed}v{validity}f{freq}.rclf"


def parse_query_time(time_str):
	return datetime.strptime(time_str, '%Y-%m-%d %H:%M:%S')


def evaluate(file_name):
	f = open(file_name)
	name = f.readline().strip()
	true_positive = 0
	true_negtive = 0
	ap_correct = 0
	total = 0
	while name != '':
		query_time_str = f.readline().strip()
		truth = f.readline().strip()
		if name not in table_name:
			print('No table found for name: %s' % name)
			name = f.readline()
			continue
		table_name = tablenames[name]
		state, ap = answer_query(parse_query_time(query_time_str), name, table_name)
		total += 1
		if truth == 'out' and state == 'out':
			true_negtive += 1
		if truth != 'out' and state == 'in':
			true_positive += 1
		if ap == truth:
			ap_correct += 1
		print('%s Truth: %s at %s' % (name, truth, query_time_str))
		name = f.readline().strip()
	print('Total: %d, True_pos: %d (%.2f), True_neg: %d (%.2f)' % (
		total, true_positive, true_positive / total, true_negtive, true_negtive / total))


if __name__ == '__main__':
	answer_query_new(datetime(2018, 3, 27, 11, 0, 0), 'primal', 'primal_20171120to20180817')
