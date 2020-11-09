from flask import Flask
from flask_restful import Resource, Api, reqparse
import learning
from datetime import datetime

app = Flask(__name__)
api = Api(app)


class Query(Resource):
	def get(self):
		parser = reqparse.RequestParser()
		para_list = ['table_name', 'user_name', 'time']
		for p in para_list:
			parser.add_argument(p)
		args = parser.parse_args()
		query_time = datetime.strptime(args['time'], '%Y-%m-%d %H:%M:%S')
		state, ap = learning.answer_query(query_time, args['user_name'], args['table_name'])
		if state == 1:
			return 'in ' + ap
		else:
			return 'out'


class FullQuery(Resource):
	def get(self):
		parser = reqparse.RequestParser()
		para_list = ['table_name', 'user_name', 'time', 'pos', 'neg', 'start_day', 'end_day', 'step']
		for p in para_list:
			parser.add_argument(p)
		args = parser.parse_args()
		query_time = datetime.strptime(args['time'], '%Y-%m-%d %H:%M:%S')
		pos = int(args['pos'])
		neg = int(args['neg'])
		step = int(args['step'])
		state, ap = learning.answer_query(query_time, args['user_name'], args['table_name'], args['start_day'],
										  args['end_day'], pos, neg, step)
		if state == 1:
			return 'in ' + ap
		else:
			return 'out'


class NewQuery(Resource):
	def get(self):
		parser = reqparse.RequestParser()
		para_list = ['table_name', 'user_name', 'time']
		for p in para_list:
			parser.add_argument(p)
		args = parser.parse_args()
		query_time = datetime.strptime(args['time'], '%Y-%m-%d %H:%M:%S')
		state, ap = learning.answer_query_new(query_time, args['user_name'], args['table_name'], fast=True)
		if state == 1:
			return 'in ' + ap
		else:
			return 'out'


class NewFullQuery(Resource):
	def get(self):
		parser = reqparse.RequestParser()
		para_list = ['table_name', 'user_name', 'time', 'start', 'end', 'pos', 'neg', 'validity', 'pos_r', 'neg_r',
					 'freq']
		for p in para_list:
			parser.add_argument(p)
		args = parser.parse_args()
		query_time = datetime.strptime(args['time'], '%Y-%m-%d %H:%M:%S')
		state, ap = learning.answer_query_new(query_time, args['user_name'], args['table_name'], args['start'],
											  args['end'], int(args['pos']), int(args['neg']), int(args['validity']),
											  int(args['pos_r']), int(args['neg_r']), float(args['freq']), False, True)
		if state == 1:
			return 'in ' + ap
		else:
			return 'out'


class QueryWithoutCache(Resource):
	def get(self):
		parser = reqparse.RequestParser()
		para_list = ['mac', 'time']
		for p in para_list:
			parser.add_argument(p)
		args = parser.parse_args()
		query_time = datetime.strptime(args['time'], '%Y-%m-%d %H:%M:%S')
		state, ap = learning.answer_query_without_cache(args['mac'], query_time)
		if state == 1:
			return 'in ' + ap
		else:
			return 'out'

api.add_resource(Query, '/query')
api.add_resource(FullQuery, '/fquery')
api.add_resource(NewQuery, '/newquery')
api.add_resource(NewFullQuery, '/newfquery')
api.add_resource(QueryWithoutCache, '/nocachequery')

if __name__ == '__main__':
	app.run(debug=True, host='127.0.0.1', port=9096)
