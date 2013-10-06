package data;

import java.awt.Color;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Random;

import data.PositionScoreCalculator.Position;

import model.ClubRecords;
import model.Contract;
import model.Hjaelper;
import model.MatchEngine;
import model.Player;
import model.Settings;
import model.Team;
import model.TransferTarget;
import model.ClubRecords.ClubRecordType;

public class ClubsDAO {
	private static Connection con;
	private static Statement stmt;
	private static int tacticsChangeNumber = 0;	
	private static Random ran = new Random();
	
	private ClubsDAO(){}
	
	public static void closeConnection() throws ClassNotFoundException, SQLException{
		if (!stmt.isClosed()) 
			stmt.close();
		if (!con.isClosed())
			con.close();
	}
	private static void openConnection(String host, String user, String pw) throws ClassNotFoundException, SQLException{
		Class.forName("org.postgresql.Driver");
		String url = "jdbc:postgresql://" + host + "/cake_footiesite?user=" + user + "&password=" + pw;
		if(con == null || con.isClosed()){
			con = DriverManager.getConnection(url);
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);	
		}
	}
	public static void openConnection(String conn) throws ClassNotFoundException, SQLException{
		Class.forName("org.postgresql.Driver");
		if(con == null || con.isClosed()){
			con = DriverManager.getConnection(conn);
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);	
		}
	}
	public static void openConnection() throws ClassNotFoundException, SQLException{
		if(con == null || con.isClosed()){
			Settings s = Settings.getInstance();
			openConnection(s.getSqlConn());
		}
	}
	
	/*
	 * Get the amount of money the club can spend on transfers
	 */
	public static int getRemainingTransferBudget(int clubId){
		String sql = "SELECT money, (SELECT SUM(amount) FROM club_incomes i INNER JOIN matches m ON i.ext_id=m.id INNER JOIN leagues l ON m.league_id=l.id WHERE cup=false AND club_id=c.id AND type=1 AND date < (SELECT MAX(firstday) FROM seasons) AND date > (SELECT firstday FROM seasons ORDER BY lastday DESC LIMIT 1 OFFSET 1)) as limit FROM clubs c WHERE id = " + clubId;
		int result = 0;
		
		try{
			ResultSet res = stmt.executeQuery(sql);
			while (res.next()){
				//The club is allowed to go into minus - but only as much as the total income they generated from league matches last season
				result = res.getInt("money") + res.getInt("limit");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	/**
	 * Gets contract information on all players at a club
	 * @param clubId the id of the club 
	 * @return ArrayList<Contract> a list of contracts - each contract has information on the player. 
	 */
	public static ArrayList<Contract> getClubCurrentPlayers(int clubId){
		
		ArrayList<Contract> result = new ArrayList<Contract>();
		String sql = "SELECT *, (SELECT count(*) FROM contracts WHERE club_id=" + clubId + 
				" AND person_id=p.id AND acceptedbyplayer=false) as extensions FROM contracts c INNER JOIN persons p ON c.person_id=p.id " +
		"INNER JOIN player_ratings r ON p.id=r.person_id WHERE acceptedbyplayer=true and " +
				"enddate>now() AND club_id=" + clubId;
			
		ResultSet res = null;
		try {
			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			res = stmt.executeQuery(sql);
		
			while (res.next()){
				Contract c = new Contract();	
				c.id = res.getInt(1);
				c.dateOffered = res.getDate("dateoffered");
				c.endDate = res.getDate("enddate");
				c.goalBonus = res.getInt("goalbonus");
				c.assistBonus = res.getInt("assistbonus");
				c.minRelease = res.getInt("minimumreleaseclause");
				c.transferFee = res.getInt("transferfee");
				c.signOnFee = res.getInt("signonfee");
				c.wage = res.getInt("weeklywage");
				c.offered = res.getBoolean("offered");
				if (res.getInt("extensions") > 0) c.extensionOffered = true;
				c.acceptedByClub = res.getBoolean("acceptedbyclub");
				c.acceptedByPlayer = res.getBoolean("acceptedbyplayer");
				c.negotiations = res.getInt("negotiations");
				c.person_id = res.getInt("person_id");
				c.club_id = clubId;
				
				Player p = new Player();
				p = new Player();
				p.setId(res.getInt("person_id"));
				p.userId = res.getInt("user_id");
				p.value = res.getInt("value");
				p.setAge(res.getInt("age"));
				p.setFirstname(res.getString("firstName"));
				p.setLastname(res.getString("lastName"));
				p.setAcceleration(res.getDouble("acceleration"));
				p.setTopSpeed((res.getDouble("topSpeed")*0.75) + 96 + 40);			
				p.setDribbling(res.getDouble("dribbling"));
				p.setStrength(res.getDouble("strength"));
				p.setTackling(res.getDouble("tackling"));
				p.setAgility(res.getDouble("agility"));
				p.setReaction(res.getDouble("reaction"));
				p.setHandling(res.getDouble("handling"));
				p.setShooting(res.getDouble("shooting"));
				p.setShotpower(res.getDouble("shotPower"));
				p.setPassing(res.getDouble("passing"));
				p.setTechnique(res.getDouble("technique"));
				p.setHeight(res.getDouble("height"));
				p.setVision(res.getDouble("vision"));
				p.setJumping(res.getDouble("jumping"));
				p.setStamina(res.getDouble("stamina"));
				p.setShirtNumber(res.getInt("shirtnumber"));
				p.setHeading(res.getDouble("heading")); 
				p.setMarking(res.getDouble("marking"));
				p.setEnergy(res.getDouble("energy"));
				p.setMorale(res.getDouble("morale"));
				p.setCommandOfArea(res.getDouble("commandofarea"));
				p.setShotstopping(res.getDouble("shotstopping"));
				p.setRushingout(res.getDouble("rushingout"));
				p.setBestPositionScore(res.getInt("best_rating"));
				
				c.player = p;
				result.add(c);
			}			
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		return result;
	}
	
	/*
	 * Gets a players listed price
	 * @Return the listed price for the player - -1 if the player is not transfer listed
	 */
	public static int getPlayerListPrice(int personId){
		int result = -1;
		String sql = "SELECT playerprice FROM transferlist WHERE person_id=" + personId;
		try {
			ResultSet res = stmt.executeQuery(sql);

			if (res.next()){
				result = res.getInt("playerprice");
			}
			res.close();
		} catch (Exception e) {
			e.printStackTrace();
		}		
		return result;
	}
	
	public static void createContractOffer(Contract c, boolean playerHasClub){
		String acceptedByClub = "false";
		if (!playerHasClub) acceptedByClub = "true";
		
		String sql = "INSERT INTO contracts(club_id, person_id, enddate, weeklywage, goalbonus, " + 
            "assistbonus, minimumreleaseclause, role, transferfee, signonfee, " +
            "acceptedbyclub, acceptedbyplayer, dateoffered, offered, negotiations) " +
    "VALUES (" + c.club_id + ", " + c.person_id + ", " + Hjaelper.dateToSQLString(c.endDate) + ", " + c.wage + ", " + c.goalBonus + ", " +
            c.assistBonus + ", " + c.minRelease + ", 1, " + c.transferFee + ", " + c.signOnFee + ", " +
            acceptedByClub + ", false, now(), true, 0);";
		
		try{
			stmt.execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Gets information on offers for players sent out by a club or offers for players at a club
	 * @param clubId the id of the club 
	 * @param getOffersSent if true a list of offers sent by the club will be returned - if false a list of offers on players currently at the club will be returned
	 * @return ArrayList<Contract> a list of contracts (offers) - each contract has information on the player. 
	 */
	public static ArrayList<Contract> getClubCurrentOffers(int clubId, boolean getOffersSent){
		
		ArrayList<Contract> result = new ArrayList<Contract>();
		String sql = "SELECT * FROM contracts c INNER JOIN persons p ON c.person_id=p.id " +
				"INNER JOIN player_ratings r ON p.id=r.person_id WHERE retired = false AND not dateoffered is null " +
				"AND acceptedbyclub=false AND club_id=" + clubId;
		if (!getOffersSent){
			sql = "SELECT * FROM contracts c INNER JOIN persons p ON c.person_id=p.id " +
					"INNER JOIN player_ratings r ON p.id=r.person_id WHERE acceptedbyclub=false AND c.person_id IN " +
					"(SELECT person_id FROM contracts WHERE enddate>now() AND retired = false AND not dateoffered is null AND acceptedbyplayer=true AND club_id=" + clubId + ")";
		}
			
		ResultSet res = null;
		try {
			res = stmt.executeQuery(sql);
		
			while (res.next()){
				Contract c = new Contract();	
				c.id = res.getInt(1);
				c.dateOffered = res.getDate("dateoffered");
				c.endDate = res.getDate("enddate");
				c.goalBonus = res.getInt("goalbonus");
				c.assistBonus = res.getInt("assistbonus");
				c.minRelease = res.getInt("minimumreleaseclause");
				c.transferFee = res.getInt("transferfee");
				c.signOnFee = res.getInt("signonfee");
				c.wage = res.getInt("weeklywage");
				c.offered = res.getBoolean("offered");
				c.acceptedByClub = res.getBoolean("acceptedbyclub");
				c.acceptedByPlayer = res.getBoolean("acceptedbyplayer");
				c.negotiations = res.getInt("negotiations");
				c.person_id = res.getInt("person_id");
				c.club_id = res.getInt("club_id");
				
				Player p = new Player();
				p = new Player();
				p.setId(res.getInt("person_id"));
				p.userId = res.getInt("user_id");
				p.value = res.getInt("value");
				p.setAge(res.getInt("age"));
				p.setFirstname(res.getString("firstName"));
				p.setLastname(res.getString("lastName"));
				p.setAcceleration(res.getDouble("acceleration"));
				p.setTopSpeed((res.getDouble("topSpeed")*0.75) + 96 + 40);			
				p.setDribbling(res.getDouble("dribbling"));
				p.setStrength(res.getDouble("strength"));
				p.setTackling(res.getDouble("tackling"));
				p.setAgility(res.getDouble("agility"));
				p.setReaction(res.getDouble("reaction"));
				p.setHandling(res.getDouble("handling"));
				p.setShooting(res.getDouble("shooting"));
				p.setShotpower(res.getDouble("shotPower"));
				p.setPassing(res.getDouble("passing"));
				p.setTechnique(res.getDouble("technique"));
				p.setHeight(res.getDouble("height"));
				p.setVision(res.getDouble("vision"));
				p.setJumping(res.getDouble("jumping"));
				p.setStamina(res.getDouble("stamina"));
				p.setShirtNumber(res.getInt("shirtnumber"));
				p.setHeading(res.getDouble("heading")); 
				p.setMarking(res.getDouble("marking"));
				p.setEnergy(res.getDouble("energy"));
				p.setMorale(res.getDouble("morale"));
				p.setCommandOfArea(res.getDouble("commandofarea"));
				p.setShotstopping(res.getDouble("shotstopping"));
				p.setRushingout(res.getDouble("rushingout"));
				
				c.player = p;
				result.add(c);
			}			
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		return result;
	}
	
	/**
	 * Deletes a contract
	 */
	public static void deleteContract(int contractId, int personId, int clubId, int playerCurrentClubId, boolean createFailedNegotiation){		
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			if (createFailedNegotiation){
				String currentClubStr = ""+playerCurrentClubId;
				if (playerCurrentClubId == -1) currentClubStr = " NULL ";
				String sql2 = "INSERT INTO failed_contract_negotiations (offer_from_club_id, offer_to_club_id, person_id) VALUES " +
						"("+clubId+","+currentClubStr+","+personId+")"; 
				stmt.execute(sql2);
			}
			String sql = "DELETE FROM contracts WHERE id="+contractId;
			stmt.execute(sql);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}
	
	public static void updateContractOffer(Contract c){
		String acceptedByClub = "false";
		if (c.acceptedByClub) acceptedByClub = "true";
		
		String acceptedByPlayer = "false";
		if (c.acceptedByPlayer) acceptedByPlayer = "true";
		
		String offered = "false";
		if (c.offered) offered = "true";
		
		String sql = "UPDATE contracts SET enddate=" + Hjaelper.dateToSQLString(c.endDate) + 
				", weeklywage=" + c.wage + ", goalbonus=" + c.goalBonus + ", " + 
            "assistbonus=" + c.assistBonus + ", minimumreleaseclause=" + c.minRelease + ", " +
				"transferfee=" + c.transferFee + ", signonfee=" + c.signOnFee + ", " +
            "acceptedbyclub=" + acceptedByClub + ", acceptedbyplayer=" + acceptedByPlayer + ", " +
            "dateoffered=" + Hjaelper.dateToSQLString(c.dateOffered) + ", offered=" + offered + ", " + 
            "negotiations=negotiations+1 WHERE id=" + c.id ;
		
		try{
			stmt.execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public static Team loadTeam(int id, MatchEngine m, boolean loadRecords, boolean writeMatchTeamTactics) {
		Team result = null;
		String sql = "SELECT * FROM clubs c INNER JOIN leagues l ON l.id=c.league_id WHERE c.id=" + id;
		ResultSet res;
		
		try {
			res = stmt.executeQuery(sql);
			String navn = "";
			Color color1 = Color.BLACK;
			Color color2 = Color.BLACK;
			int rep;
			int fame;
			int userId = 0;

			if (res.next()) {
				navn = res.getString("clubName");
				rep = res.getInt("leagueReputation");
				color1 = Hjaelper.getNewColor(res.getString("firstColor"));
				color2 = Hjaelper.getNewColor(res.getString("secondColor"));
				//			color1 = getColor(res.getInt("firstColor"));
				//			color2 = getColor(res.getInt("secondColor"));
				fame = res.getInt("fame");
				
				result = new Team(navn, color1, color2, id, m, rep, fame);
				result.setUserId(res.getInt("user_id"));
				result.ownerId = res.getInt("user_id");
				result.leagueId = res.getInt("league_id");
			}

			
			if (res != null) res.close();

			if (loadRecords){
				//Load club records related to matches
				ClubRecords records = new ClubRecords();
				ClubRecordType type = ClubRecordType.AssistsInSeason;

				sql = "SELECT * FROM club_records r INNER JOIN club_record_types t ON r.type_id=t.id WHERE club_id=" + id;
				res = stmt.executeQuery(sql);

				while (res.next()) {
					type = ClubRecordType.values()[res.getInt("type_id")];

					switch (type){

					case LosingStreak:
						records.highestAttendance = res.getInt("value");
						break;

					case HighestAttendance:
						records.highestAttendance = res.getInt("value");
						break;

					case AssistsInSeason:
						records.assistsInASeason = res.getInt("value");
						break;

					case GoalsInMatch:
						records.goalsInAMatch = res.getInt("value");
						break;

					case GoalsInSeason:
						records.goalsInASeason = res.getInt("value");
						break;

					case LowestAttendance:
						records.lowestAttendance = res.getInt("value");
						break;

					case OldestPlayer:
						records.oldestPlayer = res.getInt("value");
						break;

					case YoungestPlayer:
						records.youngestPlayer = res.getInt("value");
						break;

					case UnbeatenRun:
						records.unbeatenRun = res.getInt("value");
						break;

					case WinningStreak:
						records.winningStreak = res.getInt("value");
						break;

					case CurrLosingStreak:
						records.currLosingStreak = res.getInt("value");
						break;

					case CurrUnbeatenRun :
						records.currUnbeatenRun = res.getInt("value");
						break;

					case CurrWinningStreak:
						records.currWinningStreak = res.getInt("value");
						break;
					}
				}
				result.setRecords(records);

				if (res != null) res.close();
			}

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (writeMatchTeamTactics){
			DAO.updateDBWithQuery("INSERT INTO match_teamtactics (SELECT * FROM teamtactics WHERE club_id = " + id + ");");
		}
		return result;
	}
	
	/**
	 * Gets all the players connected to a club at the time of asking
	 * @param team the club we're asking for
	 * @return an ArrayList<Player>
	 * @throws SQLException
	 * @throws ClassNotFoundException
	 */
	public static ArrayList<Player> loadAllClubPlayers(Team team)throws SQLException, ClassNotFoundException{		
		stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);		
		String sql = "SELECT DISTINCT * FROM contracts c JOIN persons p ON c.person_id = p.id  WHERE c.club_id = " + team.getId() + " AND " +
				"c.startdate < now() AND c.enddate > now() AND c.acceptedbyplayer = 't'";
		ResultSet res = stmt.executeQuery(sql);
		ArrayList<Player> players = new ArrayList<Player>();
		
		while (res.next()){
//			System.out.println(res.getString("firstName") + " " + res.getString("lastName"));
			Player p = new Player();
			p.setId(res.getInt("person_id"));
			p.setAge(res.getInt("age"));
			p.setFirstname(res.getString("firstName"));
			p.setLastname(res.getString("lastName"));
			p.setAcceleration(res.getDouble("acceleration"));
			p.setTopSpeed((res.getDouble("topSpeed")*0.75) + 96 + 40);				
			p.setDribbling(res.getDouble("dribbling"));
			p.setStrength(res.getDouble("strength"));
			p.setTackling(res.getDouble("tackling"));
			p.setAgility(res.getDouble("agility"));
			p.setReaction(res.getDouble("reaction"));
			p.setHandling(res.getDouble("handling"));
			p.setShooting(res.getDouble("shooting"));
			p.setShotpower(res.getDouble("shotPower"));
			p.setPassing(res.getDouble("passing"));
			p.setTechnique(res.getDouble("technique"));
			p.setHeight(res.getDouble("height"));
			p.setVision(res.getDouble("vision"));
			p.setJumping(res.getDouble("jumping"));
			p.setStamina(res.getDouble("stamina"));
			p.setShirtNumber(res.getInt("shirtnumber"));
			p.setHeading(res.getDouble("heading")); 
			p.setMarking(res.getDouble("marking"));
			p.setEnergy(res.getDouble("energy"));
			p.setMorale(res.getDouble("morale"));
			p.setCommandOfArea(res.getDouble("commandofarea"));
			p.setShotstopping(res.getDouble("shotstopping"));
			p.setRushingout(res.getDouble("rushingout"));

			p.setStart_acceleration(p.getAcceleration());
			p.setStart_agility(p.getAgility());
			p.setStart_dribbling(p.getDribbling());
			p.setStart_Energy(p.getEnergy());
			p.setStart_morale(p.getMorale());
			p.setStart_handling(p.getHandling());
			p.setStart_heading(p.getHeading());
			p.setStart_passing(p.getPassing());
			p.setStart_jumping(p.getJumping());
			p.setStart_marking(p.getMarking());
			p.setStart_reaction(p.getReaction());
			p.setStart_shooting(p.getShooting());
			p.setStart_shotpower(p.getShotpower());
			p.setStart_strength(p.getStrength());
			p.setStart_tackling(p.getTackling());
			p.setStart_technique(p.getTechnique());
			p.setStart_topSpeed(p.getTopSpeed());
			p.setStart_vision(p.getVision());
			p.setStart_commandofarea(p.getCommandOfArea());
			p.setStart_shotstopping(p.getShotstopping());
			p.setStart_rushingout(p.getRushingout());
			
			p.setMyTeam(team);
			players.add(p);
		}
		if (res != null) res.close();	
		return players;
	}
	
	/*
	 * Get the amount of money the club can spend on new wages
	 */
	public static int getRemainingWageBudget(int clubId){
		//Check current wages and match income from last season. In current wages we include wages from contracts we have offered players if their current club has accepted the offer.
		String sql = "SELECT (SELECT SUM(amount) FROM club_incomes i INNER JOIN matches m ON i.ext_id=m.id INNER JOIN leagues l ON m.league_id=l.id WHERE cup=false AND club_id=c.id AND type=1 AND date < (SELECT MAX(firstday) FROM seasons) AND date > (SELECT firstday FROM seasons ORDER BY lastday DESC LIMIT 1 OFFSET 1)) as limit, (SELECT SUM(weeklywage) FROM contracts WHERE acceptedbyclub=true AND enddate > now() AND club_id=12) as wages FROM clubs c WHERE id = " + clubId;
		int result = 0;		
		try{
			ResultSet res = stmt.executeQuery(sql);
			while (res.next()){
				//The club is allowed to spend match income from last season / 73 on wages
				result = res.getInt("limit") / 73;
				result -= res.getInt("wages");				
				if (result < 0) result = 0;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}		
		return result;
	}
	
	/**
	 * Find a possible transfer target based on position, price, wage demands
	 */
	public static ArrayList<TransferTarget> findPossibleTransferTargets(int clubId, int leagueIdForClubLooking, Position position, int maxValue, int maxWages){		
		int lid = leagueIdForClubLooking;
		String pos = "";
		switch(position){
			case GK:
				pos = "gk_rating";
				break;
			case CB:
				pos = "cb_rating";
				break;
			case FB:
				pos = "fb_rating";
				break;
			case CM:
				pos = "cm_rating";
				break;
			case WG:
				pos = "wg_rating";
				break;
			case ST:
				pos = "st_rating";
				break;
		}
		
		//Check current wages and match income from last season. In current wages we include wages from contracts we have offered players if their current club has accepted the offer.
		String sql = "SELECT r.person_id, " + pos + " as positionSkill, p.user_id, lastname, age, best_rating, avg_rating, avg_match_rating_normalized, value, COALESCE(playerprice, -1) as playerprice, " +
"(SELECT count(*) FROM contracts WHERE acceptedbyplayer=true AND enddate > now() AND person_id=p.id) as hasClub, " +
"s.wage as expectedWage " +
"FROM persons p " +
"INNER JOIN player_ratings r ON p.id=r.person_id " +
"INNER JOIN wage_stats s ON r.avg_match_rating_normalized=s.rating " +
"LEFT OUTER JOIN transferlist l ON l.person_id=p.id " +
"WHERE s.wage <= " + maxWages + //"WHERE best_position = " + position.getValue() +
" AND (value * 1.5 < " + maxValue + " OR playerprice < " + maxValue + " OR (SELECT count(*) FROM contracts WHERE acceptedbyplayer=true AND enddate > now() AND person_id=p.id) = 0) " +
" AND p.id NOT IN (SELECT person_id FROM contracts WHERE enddate > now() AND club_id=" + clubId + ") " +
" AND NOT EXISTS (SELECT id FROM failed_contract_negotiations WHERE offer_from_club_id=" + clubId + " AND person_id = p.id AND (offer_to_club_id IS NULL OR offer_to_club_id=(SELECT club_id FROM contracts WHERE acceptedbyplayer=true AND enddate > now() AND person_id=p.id))) " +
"ORDER BY positionSkill DESC ";
		
		ArrayList<TransferTarget> result = new ArrayList<TransferTarget>();		
		try{
			ResultSet res = stmt.executeQuery(sql);
			while (res.next()){				
				TransferTarget tt = new TransferTarget();
				tt.personId = res.getInt("person_id");
				tt.userId = res.getInt("user_id");
				tt.lastname = res.getString("lastname");
				tt.age = res.getInt("age") / 365;
				tt.avgMatchRating = res.getInt("avg_match_rating_normalized");
				tt.avgSkill = res.getInt("avg_rating");
				tt.bestPositionSkill = res.getInt("positionSkill");
				tt.expectedWage = res.getInt("expectedwage");
				tt.value = res.getInt("value");
				tt.hasClub = res.getInt("hasclub") > 0;
				tt.listedPrice = res.getInt("playerprice");				
				result.add(tt);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return result;
	}
}