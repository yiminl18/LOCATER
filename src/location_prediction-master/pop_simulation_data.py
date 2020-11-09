import mysql.connector as myc
from random import random


def load_observations(scenario_name, file_name, dropout):
	config = dict()
	with open('log_info.txt') as f:
		info = f.readlines()
		config['user'] = info[0].strip()
		config['password'] = info[1].strip()
		config['database'] = info[2].strip()
	cnx = myc.connect(**config)
	cursor = cnx.cursor()
	table_name = scenario_name + '_observation'
	drop_table = 'DROP TABLE IF EXISTS %s' % table_name
	create_table = 'CREATE TABLE IF NOT EXISTS %s (id int, sensor_id varchar(31), ' \
				   'timestamp datetime, user int)' % table_name
	cursor.execute(drop_table)
	cursor.execute(create_table)
	f = open(file_name, 'r')
	oid_str = f.readline().strip()
	while oid_str != '':
		ap = f.readline().strip()
		ts = "'" + f.readline().strip() + "'"
		user = int(f.readline().strip())
		oid = int(oid_str[1:])
		if random() < dropout:
			insert = 'INSERT INTO %s VALUES (%d,%s,%s,%d)' % (table_name, oid, ap, ts, user)
			cursor.execute(insert)
		oid_str = f.readline().strip()
	index_user = f'CREATE INDEX idx_user ON {table_name} (user)'
	cursor.execute(index_user)
	f.close()
	cnx.commit()


def load_groundtruth(scenario_name, file_name):
	config = dict()
	with open('log_info.txt') as f:
		info = f.readlines()
		config['user'] = info[0].strip()
		config['password'] = info[1].strip()
		config['database'] = info[2].strip()
	cnx = myc.connect(**config)
	cursor = cnx.cursor()
	table_name = scenario_name + '_truth'
	drop_table = 'DROP TABLE IF EXISTS %s' % table_name
	create_table = 'CREATE TABLE IF NOT EXISTS %s (id varchar(15), wifi_ap int, ' \
				   'timestamp datetime, room int, user int)' % table_name
	cursor.execute(drop_table)
	cursor.execute(create_table)
	f = open(file_name, 'r')
	qid = f.readline().strip()
	while qid != '':
		wifi_ap = int(f.readline().strip())
		room = int(f.readline().strip())
		ts = "'" + f.readline().strip() + "'"
		user = int(f.readline().strip())
		qid = "'" + qid + "'"
		insert = f'INSERT INTO {table_name} VALUES ({qid},{wifi_ap},{ts},{room},{user})'
		# print(insert)
		cursor.execute(insert)
		qid = f.readline().strip()
	f.close()
	cnx.commit()


if __name__ == '__main__':
	load_observations('university2', r'.\data\university\ob2.txt', 0.8)
