package localizationDB;

public class Initialization {
	public static void Initialize() {
		LocalDataMaintenance.createDB("Observations");
		LocalDataMaintenance.createUserTable();
		LocalDataMaintenance.createOfficeTable();
		LocalDataMaintenance.buildOfficeTable();
		LocalDataMaintenance.loadSpaceMetadata();
		LocalDataMaintenance.createCatcheObservationListTable();
	}
}
