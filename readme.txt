Please follow the steps to run the codes.

1. You need to the ../src/location_prediction-master/, use the code python/python3 server.py to run the python codes;
2. Make sure mysql service is on. 
3. create the credential.txt in the folder with same level as src, and type in the username and password of your mysql in first two lines;
4. The code you should run is in the ../src/localizationDB/LocalizationDB.java
5. As for the results, you can see how I use the api in the LocalizationDB.java. Note that, if the building location is out, there are no probabilities distributions for room level. 

