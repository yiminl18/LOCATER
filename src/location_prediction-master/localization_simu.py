import mysql.connector as myc
import numpy as np
from datetime import datetime
from random import random
import localization, learning
import json

start_date = '20180101'
end_date = '20180630'
pos_threshold = 20
neg_threshold = 100
step_size = 4


def single_query(user, timestamp, table):
	state, ap = learning.answer_query_new(query_time=timestamp, user_name=user, table_name=table, simu=True)
	return state, ap


def read_query_file(file_name, table, dropout, baseline=False, category=False, type_file=''):
	pp = True
	cat_dict = dict()
	cat_correct = dict()
	cat_total = dict()
	if category:
		cat_dict = json.load(open(type_file))
	with open(file_name) as f:
		qid = f.readline()
		correct = 0
		total = 0
		in_correct = 0
		in_total = 0
		out_correct = 0
		out_total = 0
		while qid != '':
			wifi_ap = f.readline().strip()
			room = int(f.readline().strip())
			ts = datetime.strptime(f.readline().strip(), '%Y-%m-%d %H:%M:%S')
			user = f.readline().strip()
			if random() <= dropout:
				pp = True
				if category:
					user_type = cat_dict[str(user)]
				total += 1
				if room >= 0:
					in_total += 1
				else:
					out_total += 1
				
				if baseline:
					state, ap = coarse_baseline(ts, user, table)
				else:
					state, ap = single_query(user, ts, table)
				if (wifi_ap == ap and room >= 0) or (room < 0 and state != 1):
					correct += 1
					if room >= 0:
						in_correct += 1
					else:
						out_correct += 1

					if category:
						cat_correct[user_type] = cat_correct.get(user_type, 0) + 1
				else:
					print(f'Wrong: Query at {ts} for {user}, predicted {ap}, truth {wifi_ap}')
				if category:
					cat_total[user_type] = cat_total.get(user_type,0) + 1

			qid = f.readline().strip()
			if total % 1000 == 0 and total != 0 and pp:
				print(f'total={total}, correct={correct}')
				pp = False
		print(f'End: total={total}, correct={correct}, in_total={in_total}, in_correct={in_correct}, out_total={out_total}, out_correct={out_correct}')
		if category:
			print(f'Each type total: {cat_total}')
			print(f'Each type correct: {cat_correct}')


def coarse_baseline(query_time, user, table):
	# Return 0 means outside, 1 means inside
	# The second string means the access point if 1 is returned.

	# Assume the location data for a user exists and load all events in the queried day
	date_str = str(query_time.date()).replace('-', '')
	all_events = localization.read_local_data(date_str, date_str, table, True, user)
	if len(all_events) == 0 or len(all_events) == 1:
		print('Query for %s at %s: Not enough connection activities on that day.' % (user, query_time))
		return 0, ''

	n = len(all_events)
	if query_time < all_events[0][0] or query_time >= all_events[n - 1][0]:
		print(f'B: Query time {query_time} for {user} is outside the first event {all_events[0]} and the last event {all_events[-1]}')
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

	# online or not decision based on the duration between two events
	if p == n - 1:
		p = n - 2

	duration = (all_events[p + 1][0] - all_events[p][0]).total_seconds()
	if duration < 3600:
		return 1, all_events[p][1]
	else:
		return 0, ''


def certain_persons(file_name, table, dropout, type_file, baseline=False):
	g = json.load(open(type_file))
	category = set()
	read_query_file(file_name, table, dropout, baseline, category)


if __name__ == '__main__':
	read_query_file(r'.\data\university\truth2.txt', 'university2_observation', 0.02, baseline=False,
					category=True, type_file=r'.\data\university\usertype2.json')
