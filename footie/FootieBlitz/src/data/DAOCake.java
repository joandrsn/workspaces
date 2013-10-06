package data;

import java.awt.Color;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;

import model.ClubRecords;
import model.ClubRecords.ClubRecordType;
import model.Contract;
import model.Country;
import model.Hjaelper;
import model.Match;
import model.Match.MatchState;
import model.MatchEngine;
import model.Player;
import model.PlayerTrainingInfo;
import model.ScoutAssignment;
import model.Settings;
import model.Team;
import model.TransferTarget;

import org.postgresql.util.PSQLException;

import data.PositionScoreCalculator.Position;

/**
 *
 */
public class DAOCake {

	private static Connection con;
	private static Statement stmt;
	private static int tacticsChangeNumber = 0;	
	private static Random ran = new Random();

	private DAOCake(){

	}

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
			
	//Tested updateShootOutTest
	public static void updateShootout(int matchId, int goalsA, int goalsB) {
		DAO.updateDBWithQuery("UPDATE matches SET shootoutGoalsHome=" + goalsA + 
				", shootoutGoalsAway=" + goalsB + " WHERE id=" + matchId + ";");
	}

	// Tested setETTest
	public static void setET(int matchId) {
		DAO.updateDBWithQuery("UPDATE matches SET ET='t' WHERE id=" + matchId + ";");
	}

	//Tested addPlayerAgeTest
	public static void addPlayerAge(){
		DAO.updateDBWithQuery("UPDATE persons SET age = age + 5;");
		System.out.println("Age added");
	}
	
	//Tested addPlayerEnergyTest
	public static void addPlayerEnergy(){
		DAO.updateDBWithQuery("UPDATE persons SET energy = energy + 20 WHERE energy < 100;");
		DAO.updateDBWithQuery("UPDATE persons SET energy = 100 WHERE energy > 100;");
		System.out.println("Energy added");
	}
	
	//Tested createPlayerAttributeSnapshotsTest
	public static void createPlayerAttributeSnapshots(){
		DAO.updateDBWithQuery("INSERT INTO person_attribute_histories(person_id, age, season_id, acceleration," +
				" topspeed, dribbling, marking, energy, strength, tackling, agility, reaction, handling, " +
				"shooting, shotpower, passing, technique, jumping, stamina, heading, commandofarea, " +
				"shotstopping, rushingout, vision) (SELECT id, age, (SELECT MAX(id) FROM seasons), " +
				"acceleration, topspeed, dribbling, marking, energy, strength, tackling, agility, reaction, " +
				"handling, shooting, shotpower, passing, technique, jumping, stamina, heading, commandofarea," +
				" shotstopping, rushingout, vision FROM persons)");
	}

	public static void createPlayerAttributeSnapshotsForNewPlayers(){
		DAO.updateDBWithQuery("INSERT INTO person_attribute_histories(person_id, age, season_id, acceleration, " +
				"topspeed, dribbling, marking, energy, strength, tackling, agility, reaction, handling, " +
				"shooting, shotpower, passing, technique, jumping, stamina, heading, commandofarea, " +
				"shotstopping, rushingout, vision) (SELECT id, age, (SELECT MAX(id) FROM seasons), " +
				"acceleration, topspeed, dribbling, marking, energy, strength, tackling, agility, reaction, " +
				"handling, shooting, shotpower, passing, technique, jumping, stamina, heading, commandofarea, " +
				 "shotstopping, rushingout, vision FROM persons where id not in (select person_id from person_attribute_histories))");
	}
	
	public static void finishConstructions(){

		Statement stmt;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			Statement stmt2 = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			String sql = "SELECT *, co.id as cid FROM constructions co INNER JOIN clubs cl ON co.club_id=cl.id WHERE finished < now() AND status = 0";
			ResultSet done = stmt.executeQuery(sql);
			while (done.next()){
				System.out.println("ID: " + done.getString("cid"));

				con.setAutoCommit(false);

				try{

					String object = "training facilities";
					if (done.getInt("type") == 2){
						object = "stadium";
						String sql2 = "UPDATE stadiums SET seats = seats + " + done.getInt("seats") + ", terraces = terraces + " + done.getInt("terraces") + " WHERE id=" + done.getInt("stadium_id");
						System.out.println(sql2);
						stmt2.execute(sql2);
					}
					else{
						String sql2 = "UPDATE clubs SET trainingfacc=trainingfacc+1 WHERE id=" + done.getInt("club_id");
						stmt2.execute(sql2);
					}

					sendMessage(done.getInt("user_id"), 99, "Construction finished", "Construction on your " + object + " has finished and your new facilities are ready to use.");

					String sql2 = "UPDATE constructions SET status=1 WHERE id=" + done.getInt("cid");
					stmt2.execute(sql2);

					con.commit();
					con.setAutoCommit(true);

				} catch (SQLException e) {
					try {
						con.rollback();
					} catch (SQLException e1) {
						e1.printStackTrace();
					}
					e.printStackTrace();
				}
			}

			done.close();
			stmt.close();
			System.out.println("Constructions checked");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns -1 if average wage couldn't be found
	 **/
	public static int getPlayerAvgWage(double avgRatingNormalized, int leagueId){

		int result = -1;

		try {
			String sql = "SELECT wage FROM wage_stats WHERE rating = " + avgRatingNormalized;
			ResultSet res = stmt.executeQuery(sql);
			if (res.next()){
				result = res.getInt("wage");
			}
			res.close();

		} catch (Exception e) {
			e.printStackTrace();
		}

		return result;
	}

	public static void createNewWageStats(){
		int currentRating = 10;
		int currentWage = 0;
		double incrementStep = 1.05;
		double maxIncrementStep = 1.3;
		
		String sql = "SELECT (SELECT max(id) FROM seasons) as season_id, avgrnd2 as rating, avg(wage)::int as wage FROM " + 
			"(SELECT " +  
			"avg_match_rating_normalized::char(1) as avgrnd,  " +
			"(avg_match_rating_normalized*10)::int as avgrnd2,  " +
			"(SELECT weeklywage FROM contracts WHERE person_id=p.id AND enddate > now() AND acceptedbyplayer=true) as wage " +
			"FROM persons p   " +
			"INNER JOIN player_ratings r ON p.id=r.person_id " + 
			"WHERE (SELECT weeklywage FROM contracts WHERE person_id=p.id AND enddate > now() AND acceptedbyplayer=true) > -1) a " + 
			"group by avgrnd2 order by avgrnd2";
		
		try {
			stmt.execute("DELETE FROM wage_Stats");
			
			ResultSet res = stmt.executeQuery(sql);
			Statement stmt2 = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			
			int seasonId = 0;
			
			while (res.next()){

				seasonId = res.getInt("season_id");

				//if there's one or more ratings missing insert them first
				while (res.getInt("rating") - currentRating > 1){
					currentRating += 1;
					currentWage = (int)(currentWage * incrementStep);
					String newStat = "INSERT INTO wage_stats (season_id, rating, wage) VALUES (" +
							  seasonId + ", " + Hjaelper.round(currentRating/10d, 1) + ", " + currentWage + ")";
					stmt2.execute(newStat);
				}

//				currentWage = (int)(currentWage * incrementStep);
				
				currentRating = res.getInt("rating");
				if ((int)(res.getInt("wage")) < currentWage * incrementStep)
					currentWage = (int)(currentWage * incrementStep);
				else if ((int)(res.getInt("wage")) > currentWage * maxIncrementStep)
					currentWage = (int)(currentWage * maxIncrementStep);
				else
					currentWage = (int)(res.getInt("wage"));
				
				//First step on the wage ladder is always 100 to keep a baseline. 
				if (currentRating == 10) currentWage = 100;
				
				String newStat = "INSERT INTO wage_stats (season_id, rating, wage) VALUES (" +
						seasonId + ", " + Hjaelper.round(currentRating/10d, 1) + ", " + currentWage + ")";
				stmt2.execute(newStat);

			}

			//fill out the last season
			while (currentRating < 100){
				currentRating += 1;
				currentWage = (int)(currentWage * incrementStep);
				
				String newStat = "INSERT INTO wage_stats (season_id, rating, wage) VALUES (" +
						seasonId + ", " + Hjaelper.round(currentRating/10d, 1) + ", " + currentWage + ")";
				stmt2.execute(newStat);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		System.out.println("wage stats written");
	}
	
	//Tested poorly getPlayerAverageRatingTest
	public static double getPlayerAvgRating(int id, boolean normalizeForFame){
		double result = 0;
		ArrayList<LinkedHashMap<String, Object>> res = null;
		try {
			String sql = "SELECT (SELECT AVG(rating) FROM " +
					"(SELECT (1+rating*(league_fame-1)/10) as rating FROM match_playerstats s INNER JOIN matches m ON s.match_id=m.id WHERE " + 
					"person_id=p.id AND rating > 0 AND matchdate > now() - interval '150 days' ORDER BY matchdate DESC LIMIT 60) rat)::char(3) as avgrnd  " +
					" FROM persons p WHERE (SELECT count(*) FROM match_playerstats ssss INNER JOIN matches mmmm ON ssss.match_id=mmmm.id WHERE person_id=p.id AND rating > 0 AND matchdate > now() - interval '150 days') > 59 AND id=" + id + 
					"UNION SELECT ((COALESCE((SELECT SUM(1+rating*(league_fame-1)/10) FROM  " +
				    "match_playerstats ss INNER JOIN matches mm ON ss.match_id=mm.id WHERE person_id=p.id AND rating > 0 AND matchdate > now() - interval '150 days'), 0) + (SELECT 60 - COUNT(*) FROM " + 
				    "match_playerstats sss INNER JOIN matches mmm ON sss.match_id=mmm.id WHERE person_id=p.id AND rating > 0 AND matchdate > now() - interval '150 days')) / 60.0)::char(3) as avgrnd FROM  " +
				    "persons p WHERE (SELECT count(*) FROM match_playerstats ssss INNER JOIN matches mmmm ON ssss.match_id=mmmm.id WHERE person_id=p.id AND rating > 0 AND matchdate > now() - interval '150 days') " +
				    " < 60 AND id=" + id;
			
			if (!normalizeForFame){
				sql = "SELECT (SELECT AVG(rating) FROM " +
						"(SELECT rating FROM match_playerstats s INNER JOIN matches m ON s.match_id=m.id WHERE " +
						"person_id=p.id AND rating > 0 ORDER BY matchdate DESC LIMIT 60) rat)::char(3) as avgrnd " +
						"FROM persons p WHERE (SELECT count(*) FROM match_playerstats WHERE person_id=p.id AND " +
						"rating > 0) > 59 AND id=" + id + " UNION SELECT ((COALESCE((SELECT SUM(rating) FROM " +
					    "match_playerstats WHERE person_id=p.id AND rating > 0), 0) + (SELECT 60 - COUNT(*) FROM " +
					    "match_playerstats WHERE person_id=p.id AND rating > 0)) / 60.0)::char(3) as avgrnd FROM " +
					    "persons p WHERE (SELECT count(*) FROM match_playerstats WHERE person_id=p.id AND rating > 0)" +
					    " < 60 AND id=" + id;
			}
			res = (ArrayList<LinkedHashMap<String, Object>>)DAO.selectFromDBWithQuery(sql);
			
			for(int i = 0; i < res.size(); i++){
				result = Double.parseDouble((String)res.get(i).get("avgrnd"));
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
			return result;
	}

	public static void NPCReactToContracts(){

		Statement stmt;
		Statement stmt2;
		Statement stmt3;

		//done er en liste af id p� spillere der lige har skrevet kontrakt og ikke skal skrive under p� andre. De springes over
		ArrayList<Integer> done = new ArrayList<Integer>();

		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			stmt2 = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			stmt3 = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			String sql = "select p.user_id as agent_id,  firstname, lastname, transferfee, age, p.id as pid, weeklywage, c.signonfee, c.id as cid, c.club_id, extract(days from enddate-now()) as days, (SELECT user_id FROM clubs WHERE id=c.club_id) as user_id, (SELECT league_id FROM clubs WHERE id=c.club_id) as league_id, (SELECT EXTRACT(epoch FROM age(to_timestamp(login))/86400)::int FROM users WHERE id = p.user_id) as days_since_agent_logon from persons p INNER JOIN contracts c ON p.id=c.person_id WHERE c.enddate > now() AND c.acceptedbyplayer='f' AND c.offered=true AND c.acceptedbyclub='t' AND (p.npc = 't' OR (SELECT EXTRACT(epoch FROM age(to_timestamp(login))/86400)::int FROM users WHERE id = p.user_id) > 20) order by dateoffered;";
			ResultSet offer = stmt.executeQuery(sql);

			while (offer.next()){

				if (done.contains(offer.getInt("pid"))){
					System.out.println("Kontrakt " + offer.getInt("cid") + " - spiller er behandlet i mellemtiden og springes over.");
					System.out.println("pid: " + offer.getInt("pid") + " - club_id: " + offer.getInt("club_id"));
				}
				else{

					System.out.println("Kontrakt " + offer.getInt("cid"));

					sql = "SELECT (SELECT league_id FROM clubs WHERE id = c.club_id) as league_id, club_id, weeklywage, extract(days from enddate-now()) as days FROM contracts c WHERE person_id=" + offer.getInt("pid") + " AND acceptedbyplayer='t' AND enddate>now();";
					ResultSet currCon = stmt2.executeQuery(sql);

					boolean ok = true;
					String problem = "";
					int currClub = -1;
					int currLeague = -1;

					int expectedDays = 73 * 2 - 5;
					double avgRating = getPlayerAvgRating(offer.getInt("pid"), true);
					int expectedWage = getPlayerAvgWage(avgRating, offer.getInt("league_id"));
					boolean extension = false;

					if (currCon.next()){ //Spilleren har en nuv�rende klub

						currClub = currCon.getInt("club_id");
						currLeague = currCon.getInt("league_id");

						//Hvis det er den nuv�rende klub der tilbyder en ny kontrakt accepteres den baseret p� wage
						if (offer.getInt("club_id") == currCon.getInt("club_id")){
							extension = true;

							if (offer.getInt("weeklywage") < expectedWage){
								problem = "The offered wage is unacceptable. I would only consider offers with a wage of at least " + expectedWage + ". ";
								ok = false;
							}
							if (offer.getInt("days") < expectedDays){
								if (problem.length() > 0)
									problem += "I also expect the contract to last at least two seasons. ";
								else
									problem += "I expect the contract to last at least two seasons. ";

								ok = false;
							}
						}
						else{//Andre klubber

							//Accepter udspil der er ok - p� et tidspunkt skal vi m�ske tjekke p� om spilleren gider flytte til den nye klub
							if (offer.getInt("weeklywage") < expectedWage){
								problem = "The offered wage is unacceptable. I would only consider offers with a wage of at least " + expectedWage + ". ";
								ok = false;
							}
							if (offer.getInt("days") < expectedDays){
								if (problem.length() > 0)
									problem += "I also expect the contract to last at least two seasons. ";
								else
									problem += "I expect the contract to last at least two seasons. ";

								ok = false;
							}
						}
					}
					else{ //Spilleren har ikke nogen nuv�rende klub.
						//Kontrakten accepteres hvis den er nogenlunde ok

						if (offer.getInt("weeklywage") < expectedWage){
							problem = "The offered wage is unacceptable. I would only consider offers with a wage of at least " + expectedWage + ". ";
							ok = false;
						}
						if (offer.getInt("days") < expectedDays){
							if (problem.length() > 0)
								problem += "I also expect the contract to last at least two seasons. ";
							else
								problem += "I expect the contract to last at least two seasons. ";

							ok = false;
						}
					}

					System.out.println("ok: " + ok + ", problem: " + problem);

					try {

						con.setAutoCommit(false);

						if(ok){
							String accept = "UPDATE contracts SET enddate = now() WHERE enddate > now() AND acceptedbyplayer='t' AND person_id = " + offer.getInt("pid");
							stmt3.execute(accept);

							String from_club = ""+currClub;
							if (currClub == -1) from_club = "NULL";
							String from_league = ""+currLeague;
							if (currLeague == -1) from_league = "NULL";

							accept = "UPDATE contracts SET acceptedbyplayer='t', startdate=now(), from_league = " + from_league + ", to_league = " + offer.getInt("league_id") + ", from_club = " + from_club + ", season_id=(SELECT id FROM seasons WHERE firstday < now() AND lastday >= CURRENT_DATE) WHERE id = " + offer.getInt("cid");
							stmt3.execute(accept);

							if (offer.getInt("transferfee") > 0){

								accept = "INSERT INTO club_expenses (amount, type, date, description, club_id) VALUES (" + offer.getInt("transferfee") + ", 4, now(), 'Transfer fee', " + offer.getInt("club_id") + ");";
								stmt3.execute(accept);

								accept = "INSERT INTO club_incomes (amount, type, date, description, club_id) VALUES (" + offer.getInt("transferfee") + ", 4, now(), 'Transfer fee', " + currClub + ");";
								stmt3.execute(accept);
							}

							if (offer.getInt("signonfee") > 0){
								accept = "UPDATE persons SET money = money + " + offer.getInt("signonfee") + " WHERE id = " + offer.getInt("pid") + ";";
								stmt3.execute(accept);

								accept = "INSERT INTO club_expenses (amount, type, date, description, club_id) VALUES (" + offer.getInt("signonfee") + ", 5, now(), 'Sign on fee', " + offer.getInt("club_id") + ");";
								stmt3.execute(accept);
							}
							
							//If the player is transferlisted, this listing should be deleted
							TransferList.deletePlayerListings(offer.getInt("pid"));
							
							accept = "DELETE FROM contracts WHERE acceptedbyplayer='f' AND person_id = " + offer.getInt("pid") + ";";
							stmt3.execute(accept);

							String shno = "select COALESCE(min(p.shirtnumber+1), 1) as shn from persons p inner join contracts c on c.person_id=p.id where enddate>now() and club_id=" + offer.getInt("club_id") + " and (select count(*) from persons q inner join contracts c on c.person_id=q.id where enddate>now() and club_id=" + offer.getInt("club_id") + " and shirtnumber=p.shirtnumber+1)=0 and p.shirtnumber+1<100";
							ResultSet resShNo = stmt3.executeQuery(shno);

							if (resShNo.next()){
								accept = "UPDATE persons SET shirtnumber=" + resShNo.getInt("shn") + " WHERE id = " + offer.getInt("pid") + ";";
								stmt3.execute(accept);
							}

							if (offer.getInt("user_id") != 99){
								if (extension)
									sendMessage(offer.getInt("user_id"), 99, offer.getString("firstname") + " " + offer.getString("lastname") + " accepts contract offer", offer.getString("firstname") + " " + offer.getString("lastname") + " has accepted your contract offer.");
								else
									sendMessage(offer.getInt("user_id"), 99, offer.getString("firstname") + " " + offer.getString("lastname") + " accepts contract offer", offer.getString("firstname") + " " + offer.getString("lastname") + " has accepted your contract offer. He has joined your squad and is available for immediate selection.");
							}
							currCon.close();
						}
						else{
							if (offer.getInt("user_id") != 99)
								sendMessage(offer.getInt("user_id"), 99, "Contract negotiation with " + offer.getString("firstname") + " " + offer.getString("lastname"), offer.getString("firstname") + " " + offer.getString("lastname") + "  has rejected your contract offer adding the following comment: " + problem + " He has sent a negotiated offer that matches his expectations.");

							String accept = "UPDATE contracts SET offered=false ";

							if (offer.getInt("weeklywage") < expectedWage)
								accept += ", weeklywage=" + expectedWage;

							if (offer.getInt("days") < expectedDays)
								accept += ", enddate=now() + interval '" + expectedDays + " days' ";

							accept += " WHERE id=" + offer.getInt("cid") + ";";
							stmt3.execute(accept);

							System.out.println(accept);

							currCon.close();
						}

						done.add(offer.getInt("pid"));
						con.commit();
						con.setAutoCommit(true);

					} catch (SQLException e) {
						try {
							con.rollback();
						} catch (SQLException e1) {
							e1.printStackTrace();
						}
						e.printStackTrace();
						System.out.println(e.toString());
					}
				}
			}

			offer.close();
			stmt.close();
			stmt2.close();
			stmt3.close();
			System.out.println("NPC-contracts done");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	//Tested sendMessageTest
	/**
	 * Send a message in a new thread to a user
	 */
	public static void sendMessage(int toID, int fromID, String subject, String text){
		List<LinkedHashMap<String,Object>> res = DAO.updateDBWithQueryReturnGeneratedKeys("INSERT INTO Message_threads (subject, updated) VALUES ('" + subject + "', now());");
		try {
			for (int i = 0; i < res.size(); i++){
				int id = (Integer)(res.get(i).get("id"));
				DAO.updateDBWithQuery("INSERT INTO messages (thread_id, from_user_id, to_user_id, text)" +
						" VALUES (" + id + ", " + fromID + ", " + toID + ", '" + text.replace("'", "''") + "');");
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Removes all players that are not on contract with a team, from the teams lineup
	 */
	//Tested cleanLineupsTest
	public static void cleanLineups(){
		for (int i = 1; i < 19; i++){
			DAO.updateDBWithQuery("update lineups l set pl" + i + "id=-1 where pl" + i + "id > -1 and " +
					"(select count(*) from contracts where person_id=l.pl" + i + "id and " +
					"club_id=l.club_id and acceptedbyplayer='t' and enddate > now()) = 0");
		}
		System.out.println("Lineups cleaned");
	}

	
	public static void updateInterest(){
		payInterest(5.0);
		receiveInterest(1.0);		
	}
	
	//Tested payInterestTest
	public static void payInterest(double percentage){
		DAO.updateDBWithQuery("INSERT INTO club_expenses (amount, type, date, description, club_id)" +
				" (SELECT money * ((" + percentage + " / 73.0) / 100.0) * -1.0, 10, now(), 'Interest paid', " +
				"id FROM clubs WHERE money < 0 AND money * ((5.0 / 73.0) / 100.0) * -1.0 >= 1);");
		System.out.println("Interest paid");
	}
	
	//Tested receiveInterestTest
	public static void receiveInterest(double percentage){
		DAO.updateDBWithQuery("INSERT INTO club_incomes (amount, type, date, description, club_id) " +
				"(SELECT money * ((" + percentage + " / 73.0) / 100.0), 10, now(), 'Interest received', " +
				"id FROM clubs WHERE money > 0 AND money * ((5.0 / 73.0) / 100.0) >= 1);");
		System.out.println("Interest received");
	}

//	public static Object getLinkedHashMapValueByIndex(LinkedHashMap<String, Object> hMap, int index){
//		System.out.println(hMap.values().toArray().toString());
//		for(int i = 0; i < hMap.values().size(); i++){
//			System.out.println(hMap.values());
//		}
////		System.out.println();
//		   return (Object) hMap.values().toArray()[index];
//		}


	
	public static void payWages() throws SQLException{
		payWagesPlayers();
		ArrayList<LinkedHashMap<String, Object>> rs = findPlayersOnContract();	
		payWagesClubs(rs);	
		System.out.println("wages paid");
	}
	
	//Tested payWagesPlayersTest
	public static void payWagesPlayers(){
		//Give the player his wage
		DAO.updateDBWithQuery("UPDATE persons p SET money = money + COALESCE((SELECT weeklywage FROM " +
		"contracts WHERE person_id = p.id AND enddate>now() AND acceptedbyplayer='t'), 0) - " +
		"COALESCE((SELECT percent_wage FROM agent_contracts WHERE person_id=p.id AND accepted IS NOT NULL AND cancelled IS NULL), 0) " +
		" * COALESCE((SELECT weeklywage FROM contracts WHERE person_id = p.id AND enddate>now() AND acceptedbyplayer='t'), 0) / 100;");
		
		//Give agents their share
		DAO.updateDBWithQuery("UPDATE users u SET money = money + " +
		 "COALESCE((SELECT SUM(weeklywage * percent_wage / 100) FROM agent_contracts ac " +
		 "INNER JOIN contracts c ON ac.person_id=c.person_id AND c.enddate>now() AND acceptedbyplayer=true " +
		 "WHERE user_id=u.id AND accepted IS NOT NULL AND cancelled IS NULL), 0)");
	}
	
	//Tested findPlayersOnContractIsNotEmptyTest
	/** Finds and adds the weeklywages of all players on contract at a club for all clubs
	 * @return ArrayList<LinkedHashMap<String, Object>> 
	 * (fx list(club1(<"id", 12><"weeklywage", 50000>), club2(<"id", 14><"weeklywage", 40000>))
	 */
	public static ArrayList<LinkedHashMap<String, Object>> findPlayersOnContract(){
		ArrayList<LinkedHashMap<String, Object>> rs = (ArrayList<LinkedHashMap<String, Object>>) DAO.selectFromDBWithQuery("SELECT id, COALESCE((SELECT sum(weeklywage) " +
		"FROM contracts WHERE club_id = c.id AND enddate>now() AND acceptedbyplayer='t'), 0) as sum " +
		"FROM clubs c;");
		return rs;	
	}
	
	//Tested payWagesClubsTest
	/** Adds the weeklywages of all players on contract to the expenses of their clubs
	 * @param rs all players on contract at each club and their weeklywages. (club_id, weeklywage)
	 */
	public static void payWagesClubs(ArrayList<LinkedHashMap<String, Object>> rs){
		for(int i = 0; i < rs.size(); i++){
			DAO.updateDBWithQuery("INSERT INTO club_expenses (club_id, amount, type, date, description) " +
			"VALUES (" + rs.get(i).get("id") + ", " + rs.get(i).get("sum") + ", 2, now(), 'Player wages');");
		}
	}
	
	//Not Tested
	public static void endOfSeason() throws SQLException{
		ArrayList<LinkedHashMap<String, Object>> res = getAllLeaguesWithRepLargerThan0();
		
		payLeaguePriceMoney(res);

		payLeagueSponsorshipMoney();

		createNewSeason();
		createNewWageStats();
		
		//Snapshots of all players' attributes
		createPlayerAttributeSnapshots();

		//Generate fixtures for league
		Calendar cal = Calendar.getInstance();
		//first matches of new season will start in three days
		cal.add(Calendar.DATE, 5);
		for(int i = 0; i < res.size(); i++){
			genFixtureList((Integer)res.get(i).get("id"), cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DATE), 18, 30, -1);
		}
	}
	
	//Tested poorly
	/**
	 * Returns all leagues with a reputation higher than 0. Leagues with a rep of 0 is friendly matches
	 * @return  ArrayList<LinkedHashMap<String, Object>>
	 */
	public static ArrayList<LinkedHashMap<String, Object>> getAllLeaguesWithRepLargerThan0(){
		ArrayList<LinkedHashMap<String, Object>> res;
		res = (ArrayList<LinkedHashMap<String, Object>>)DAO.selectFromDBWithQuery("SELECT *, (SELECT MAX(number) FROM seasons) as season " +
				"FROM leagues WHERE leaguereputation > 0;");		
		return res;
	}
	
	/**
	 * Pay prize money for each league
	 * @param res all leagues with reputation higher than 0
	 */
	public static void payLeaguePriceMoney(ArrayList<LinkedHashMap<String, Object>> res){		
		for(int i = 0; i < res.size(); i++){
			payLeaguePriceMoney((Integer)res.get(i).get("id"), (Integer)res.get(i).get("season"));
		}
	}

	public static void payLeaguePriceMoney(int leagueId, int season){
		Statement stmt;
		int leagueRep = 0;
		int teamsPromoted = 0;
		int teamsRelegated = 0;
		int numberOfTeams = 0;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			String sql = "SELECT leaguereputation, teams_promoted, teams_relegated, " +
			" (SELECT COUNT(*) FROM clubs c WHERE league_id=l.id AND (SELECT count(*) FROM matches  WHERE matchdate > (SELECT firstday FROM seasons WHERE number = " + season + ") AND matchdate < (SELECT lastday FROM seasons WHERE number = " + season + ") AND status = 2  AND (hometeamid=c.id OR awayteamid=c.id) AND league_id = l.id) > 0) as teams " +
			"FROM leagues l WHERE id = " + leagueId + ";";
			
			ResultSet res = stmt.executeQuery(sql);
			if (res.next()){
				leagueRep = res.getInt(1);
				teamsPromoted = res.getInt("teams_promoted");
				teamsRelegated = res.getInt("teams_relegated");
				numberOfTeams = res.getInt("teams");
			}
			else 
				leagueRep = 0;			
			res.close();
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		//Get all clubs from league with league id, in right order of position
		try{
			String matchcond = " WHERE matchdate > (SELECT firstday FROM seasons WHERE number = " + season + ") AND matchdate < (SELECT lastday FROM seasons WHERE number = " + season + ") AND status = 2 ";

			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			String sql = "SELECT *, (SELECT count(*) * 3 FROM matches " + matchcond + " AND hometeamid=c.id AND hometeamgoals>awayteamgoals AND league_id = " + leagueId + ") " +
					" + (SELECT count(*) FROM matches " + matchcond + " AND (hometeamid=c.id OR awayteamid=c.id) AND hometeamgoals=awayteamgoals AND league_id = " + leagueId + ")" +
					" + (SELECT count(*) * 3 FROM matches " + matchcond + " AND awayteamid=c.id AND hometeamgoals<awayteamgoals AND league_id = " + leagueId + ") as points, " +
					"(SELECT count(*) FROM matches " + matchcond + " AND hometeamid=c.id AND hometeamgoals>awayteamgoals AND league_id = " + leagueId + ")" +
					" + (SELECT count(*) FROM matches " + matchcond + " AND awayteamid=c.id AND hometeamgoals<awayteamgoals AND league_id = " + leagueId + ") as wins, " +
					"(SELECT count(*) FROM matches " + matchcond + " AND (hometeamid=c.id OR awayteamid=c.id) AND hometeamgoals=awayteamgoals AND league_id = " + leagueId + ") as draws, " +
					"(SELECT count(*) FROM matches " + matchcond + " AND hometeamid=c.id AND hometeamgoals<awayteamgoals AND league_id = " + leagueId + ")" +
					" + (SELECT count(*) FROM matches " + matchcond + " AND awayteamid=c.id AND hometeamgoals>awayteamgoals AND league_id = " + leagueId + ") as losses, " +
					"(SELECT count(*) FROM matches " + matchcond + " AND (hometeamid=c.id OR awayteamid=c.id) AND league_id = " + leagueId + ") as played, " +
					"(SELECT COALESCE(sum(hometeamgoals), 0) FROM matches " + matchcond + " AND hometeamid=c.id AND league_id = " + leagueId + ")" +
					" + (SELECT COALESCE(sum(awayteamgoals), 0) FROM matches " + matchcond + " AND awayteamid=c.id AND league_id = " + leagueId + ") as gfor, " +
					"(SELECT COALESCE(sum(awayteamgoals), 0) FROM matches " + matchcond + " AND hometeamid=c.id AND league_id = " + leagueId + ")" +
					" + (SELECT COALESCE(sum(hometeamgoals), 0) FROM matches " + matchcond + " AND awayteamid=c.id AND league_id = " + leagueId + ") as against, " +
					"(SELECT COALESCE(sum(hometeamgoals), 0) FROM matches " + matchcond + " AND hometeamid=c.id AND league_id = " + leagueId + ")" +
					" + (SELECT COALESCE(sum(awayteamgoals), 0) FROM matches " + matchcond + " AND awayteamid=c.id AND league_id = " + leagueId + ")" +
					" - (SELECT COALESCE(sum(awayteamgoals), 0) FROM matches " + matchcond + " AND hometeamid=c.id AND league_id = " + leagueId + ")" +
					" - (SELECT COALESCE(sum(hometeamgoals), 0) FROM matches " + matchcond + " AND awayteamid=c.id AND league_id = " + leagueId + ") as difference " +
					"FROM clubs c WHERE league_id = " + leagueId + " AND " +
					" (SELECT count(*) FROM matches " + matchcond + " AND (hometeamid=c.id OR awayteamid=c.id) AND league_id = " + leagueId + ") > 0 " +
					"ORDER BY points DESC, difference DESC, gfor DESC, played ASC, 5 ASC";
			ResultSet res = stmt.executeQuery(sql);	
			//Run through these clubs
			int clubPos = 1;

			Statement stmt2 = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			//Run everything as a transaction so no one gets paid if an error occurs
			con.setAutoCommit(false);

			while (res.next()) {
				int priceMoney = leaguePriceMoney(leagueRep, clubPos);
				//add these price money to the clubs money

				String sql2 = "INSERT INTO club_incomes (amount, type, date, description, club_id) VALUES (" + priceMoney + ", 9, now(), 'Prize money', " + res.getInt("id") + ");";
				stmt2.execute(sql2);

				//Promote top teams
				if (clubPos <= teamsPromoted){
					sql2 = "UPDATE clubs SET league_id = (SELECT promote_to_league_id FROM leagues WHERE id = " + leagueId  + ") WHERE id = " + res.getInt("id") + ";";
					stmt2.execute(sql2);
				}
				
				//Relegate bottom teams
				if (clubPos > numberOfTeams - teamsRelegated){
					sql2 = "UPDATE clubs SET league_id = (SELECT id FROM leagues WHERE promote_to_league_id = " + leagueId  + ") WHERE id = " + res.getInt("id") + ";";
					stmt2.execute(sql2);
				}
				
				clubPos += 1;
			}

			//All the queries are ready. Commit
			con.commit();
			con.setAutoCommit(true);

			stmt2.close();
			res.close();
		} catch (SQLException e) {
			//An error occurred. Roll back so no one gets paid.
			try {
				con.rollback();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		}

	}
	
	//Tested createNewSeasonTest
	public static void createNewSeason(){
		DAO.updateDBWithQuery("INSERT INTO seasons (number, firstday, lastday) VALUES ((SELECT MAX(number) " +
				"FROM seasons) + 1, (SELECT MAX(lastday) FROM seasons) + interval '1 days', " +
				"(SELECT MAX(lastday) FROM seasons) + interval '74 days')");
		DAO.updateDBWithQuery("INSERT INTO league_tables (league_id, season_id, club_id, matches, won, drawn, " +
				"goalsfor, goalsagainst) (SELECT league_id, (SELECT max(id) FROM seasons), id, 0, 0, 0, 0, 0 " +
				"FROM clubs)");
	}

	//Not Tested
	public static void payLeagueSponsorshipMoney(){
		DAO.updateDBWithQuery("INSERT INTO club_incomes (club_id, date, amount, type, description) (SELECT " +
				"id, now(), (SELECT leaguereputation FROM leagues where id=c.league_id)^3*4000, 11, 'League " +
				"sponsorship' FROM clubs c)");
		}

	//updateCompetitions generates fixtures and pays prize money for all competitions when necessary
	public static void updateCompetitions(){

		Statement stmt, stmt2;

		try{

			stmt2 = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			con.setAutoCommit(false);

			int dayOfSeason = -1;
			int daysToLastDayOfSeason = -1;
			int season_id = -1;

			String sql = "SELECT id, date_part('days', current_date-firstday)::int as day, date_part('days', lastday-current_date)::int as tolastday FROM seasons ORDER BY number DESC LIMIT 1;";
			ResultSet res = stmt.executeQuery(sql);
			if (res.next()){
				dayOfSeason = res.getInt("day");
				daysToLastDayOfSeason = res.getInt("tolastday");
				season_id = res.getInt("id");
			}
			res.close();

			System.out.println("dayOfSeason: " + dayOfSeason);
			System.out.println("daysToLastDayOfSeason: " + daysToLastDayOfSeason);

			if (dayOfSeason == 0){
				//First day of the season we pay league sponsorships and all league fixtures are generated

				payLeagueSponsorshipMoney();
				sql = "INSERT INTO logs (dt, msg, type) VALUES (now(), 'League sponsorships paid', 99);";
				stmt.execute(sql);	

				//Snapshots of all players' attributes
				createPlayerAttributeSnapshots();
				sql = "INSERT INTO logs (dt, msg, type) VALUES (now(), 'Player snapshots created', 99);";
				stmt.execute(sql);

				//Generate fixtures for leagues
				sql = "SELECT * FROM leagues WHERE cup=false AND leaguereputation > 0;";
				res = stmt.executeQuery(sql);	

				Calendar cal = Calendar.getInstance();
				//first matches of new season will start in 6 days
				cal.add(Calendar.DATE, 6);

				while (res.next()) {
					genFixtureList(res.getInt("id"), cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DATE), res.getInt("matches_start_hour"), 30, season_id);
				}
				res.close();

				sql = "INSERT INTO logs (dt, msg, type) VALUES (now(), 'League fixtures created', 99);";
				stmt.execute(sql);
			}
			else{
				Random r = new Random();

				//If it's not the first day of the season - get all enabled cup competitions with no more fixtures but more stages to play
				sql = "SELECT *, l.id as lid, s.id as sid, (SELECT id FROM competition_stages WHERE league_id=l.id AND number=l.current_stage) AS prevstageid, " +
						"(SELECT two_legs FROM competition_stages WHERE league_id=l.id AND number=l.current_stage) AS prevtwolegs, " +
						"(SELECT knockout FROM competition_stages WHERE league_id=l.id AND number=l.current_stage) as prevstageknockout " +
						"FROM leagues l LEFT OUTER JOIN competition_stages s ON l.id=s.league_id WHERE l.cup=true AND s.number = l.current_stage + 1 AND " +
						"(SELECT count(*) FROM matches WHERE league_id=l.id AND status = 0) = 0 AND enabled = true;";

				res = stmt.executeQuery(sql);
				while (res.next()){

					System.out.println("Checking " + res.getString("leaguename") + " (stage_id: " + res.getInt("sid") + ")");

					//Indexedpot is the ids of all the clubs qualified for next round and the index of their last match (used for cup tree)
					SortedMap<Integer, Integer> indexedPot = new TreeMap<Integer, Integer>();
					SortedMap<Integer, Integer> seededPot = new TreeMap<Integer, Integer>();

					//pot is all the ids of the clubs qualified for the next round
					ArrayList<Integer> pot = new ArrayList<Integer>();

					//The pots below are used for generating knockout fixtures in a cup tree after a group stage
					//A1 is all the group winners in the top half of the tree (second place from their groups will be in B2 - bottom half of the tree)
					ArrayList<Integer> potA1 = new ArrayList<Integer>();
					ArrayList<Integer> potA2 = new ArrayList<Integer>();
					ArrayList<Integer> potB1 = new ArrayList<Integer>();
					ArrayList<Integer> potB2 = new ArrayList<Integer>();

					if (res.getInt("number") > 1){
						//After the first stage of a cup we get winners from last stage

						if (!res.getBoolean("prevstageknockout")){
							//Last round was a group so the top two teams from each group goes through to the next round
							System.out.println("Last round was a group stage");

							//Get number of groups
							int numberOfGroups = 0;
							sql = "SELECT count(*) as c FROM competition_stage_groups WHERE stage_id=" + res.getInt("prevstageid") + " AND season_id=" + season_id + ";";
							ResultSet resGroupCount = stmt2.executeQuery(sql);
							if (resGroupCount.next()){

								numberOfGroups = resGroupCount.getInt("c");
								System.out.println("Number of groups: " + numberOfGroups);
							}
							resGroupCount.close();

							String matchcond = " FROM matches m WHERE season_id = " + season_id + " AND stage_id = " + res.getInt("prevstageid");

							//Find points, goals etc for all clubs in this stage of the competition
							sql = "SELECT c.id as c, csg.id as csg, ";
							sql += "(SELECT count(*)" + matchcond + " AND hometeamid=c.id AND hometeamgoals > awayteamgoals) * 3 + ";
							sql += "(SELECT count(*)" + matchcond + " AND awayteamid=c.id AND hometeamgoals < awayteamgoals) * 3 + ";
							sql += "(SELECT count(*)" + matchcond + " AND (hometeamid=c.id OR awayteamid=c.id) AND hometeamgoals = awayteamgoals) AS points, ";
							sql += "COALESCE((SELECT sum(hometeamgoals)" + matchcond + " AND hometeamid=c.id) + (SELECT sum(awayteamgoals)" + matchcond + " AND awayteamid=c.id), 0) AS gfor, ";
							sql += "COALESCE((SELECT sum(awayteamgoals)" + matchcond + " AND hometeamid=c.id) + (SELECT sum(hometeamgoals)" + matchcond + " AND awayteamid=c.id), 0) AS against, ";
							sql += "COALESCE(((SELECT sum(hometeamgoals)" + matchcond + " AND hometeamid=c.id) + (SELECT sum(awayteamgoals)" + matchcond + " AND awayteamid=c.id)) - ";
							sql += "((SELECT sum(awayteamgoals)" + matchcond + " AND hometeamid=c.id) + (SELECT sum(hometeamgoals)" + matchcond + " AND awayteamid=c.id)), 0) AS goaldiff ";
							sql += "FROM clubs c INNER JOIN stage_group_clubs gc ON c.id=gc.club_id INNER JOIN competition_stage_groups csg ON gc.competition_stage_group_id=csg.id ";
							sql += "WHERE csg.stage_id=" + res.getInt("prevstageid") + " AND csg.season_id=" + season_id + " ";
							sql += "ORDER BY points DESC, goaldiff DESC, gfor DESC ";

							ResultSet res2 = stmt2.executeQuery(sql);

							//Go through the teams from the top and add two from each group to the draw (pot) for the next round
							HashMap<Integer, Integer> teamsPerGroup = new HashMap<Integer, Integer>();
							HashMap<Integer, String> groupSecondGoesToPot = new HashMap<Integer, String>();
							while (res2.next()){

								//If the group id (competition_stage_groups(id)) hasn't been registered yet, add it and set count to 1
								if (teamsPerGroup.get(res2.getInt("csg")) == null){
									teamsPerGroup.put(res2.getInt("csg"), 1);
									pot.add(res2.getInt("c"));

									//First team from the group is the group winner. Put that team in A1 or B1
									if (potA1.size() < numberOfGroups / 2 && potB1.size() < numberOfGroups / 2){
										if (r.nextDouble() < 0.5){
											potA1.add(res2.getInt("c"));
											groupSecondGoesToPot.put(res2.getInt("csg"), "B");
										}
										else{
											potB1.add(res2.getInt("c"));
											groupSecondGoesToPot.put(res2.getInt("csg"), "A");
										}
									}
									else if (potA1.size() < numberOfGroups / 2){
										//B1 is full - put the rest in A1
										potA1.add(res2.getInt("c"));
										groupSecondGoesToPot.put(res2.getInt("csg"), "B");
									}
									else{
										//A1 is full - put the rest in B1
										potB1.add(res2.getInt("c"));
										groupSecondGoesToPot.put(res2.getInt("csg"), "A");
									}

								}
								//If one club from the group has been found already set the count to two. We now have two clubs from this group in the pot
								else if(teamsPerGroup.get(res2.getInt("csg")) == 1){
									teamsPerGroup.put(res2.getInt("csg"), 2);
									pot.add(res2.getInt("c"));

									//Second place goes to A2 or B2
									if (groupSecondGoesToPot.get(res2.getInt("csg")).equals("A")){
										potA2.add(res2.getInt("c"));
									}
									else{
										potB2.add(res2.getInt("c"));
									}
								}
							}
							res2.close();

							//Draw from the separated pots to create the indexed pots
							int index = 1;
							while (indexedPot.size() < pot.size() / 2){

								indexedPot.put(index, potA1.remove(r.nextInt(potA1.size())));
								index++;
								indexedPot.put(index, potA2.remove(r.nextInt(potA2.size())));
								index++;
							}
							while (indexedPot.size() < pot.size()){

								indexedPot.put(index, potB1.remove(r.nextInt(potB1.size())));
								index++;
								indexedPot.put(index, potB2.remove(r.nextInt(potB2.size())));
								index++;
							}

							//pot is ready
						}
						else{
							//Last round was a knockout stage so all the winners from those games are in the pot
							System.out.println("Last round was a knock out stage");
							sql = "SELECT * FROM matches m WHERE season_id = " + season_id + " AND stage_id = " + res.getInt("prevstageid") + " ORDER BY matchdate";

							ResultSet res2 = stmt2.executeQuery(sql);

							//Go through the teams from the top and add two from each group to the draw (pot) for the next round

							if (res.getBoolean("prevtwolegs")){
								//If last round was over two legs find the overall winner from each set of matches

								HashMap<Integer, Integer> homeGoals = new HashMap<Integer, Integer>();
								HashMap<Integer, Integer> awayGoals = new HashMap<Integer, Integer>();
								HashMap<Integer, Integer> against = new HashMap<Integer, Integer>();
								HashMap<Integer, Integer> shootoutgoals = new HashMap<Integer, Integer>();
								HashMap<Integer, Integer> cupIndex = new HashMap<Integer, Integer>();

								while (res2.next()){
									if (!against.values().contains(res2.getInt("hometeamid")) && !against.keySet().contains(res2.getInt("hometeamid"))){
										against.put(res2.getInt("hometeamid"), res2.getInt("awayteamid"));
									}

									homeGoals.put(res2.getInt("hometeamid"), res2.getInt("hometeamgoals"));
									awayGoals.put(res2.getInt("awayteamid"), res2.getInt("awayteamgoals"));
									cupIndex.put(res2.getInt("awayteamid"), res2.getInt("cup_index"));
									cupIndex.put(res2.getInt("hometeamid"), res2.getInt("cup_index"));

									if (res2.getInt("shootoutgoalshome") > 0 || res2.getInt("shootoutgoalsaway") > 0){
										shootoutgoals.put(res2.getInt("hometeamid"), res2.getInt("shootoutgoalshome"));
										shootoutgoals.put(res2.getInt("awayteamid"), res2.getInt("shootoutgoalsaway"));
									}

								}

								for (Integer i : against.keySet()){
									if (homeGoals.get(i) + awayGoals.get(i) > homeGoals.get(against.get(i)) + awayGoals.get(against.get(i))){
										pot.add(i);
										indexedPot.put(cupIndex.get(i), i);
									}
									else if (homeGoals.get(i) + awayGoals.get(i) < homeGoals.get(against.get(i)) + awayGoals.get(against.get(i))){
										pot.add(against.get(i));
										indexedPot.put(cupIndex.get(against.get(i)), against.get(i));
									}
									else if (awayGoals.get(i) > awayGoals.get(against.get(i))){
										pot.add(i);
										indexedPot.put(cupIndex.get(i), i);
									}
									else if (awayGoals.get(i) < awayGoals.get(against.get(i))){
										pot.add(against.get(i));
										indexedPot.put(cupIndex.get(against.get(i)), against.get(i));
									}
									else if (shootoutgoals.get(i) > shootoutgoals.get(against.get(i))){
										pot.add(i);
										indexedPot.put(cupIndex.get(i), i);
									}
									else if (shootoutgoals.get(i) < shootoutgoals.get(against.get(i))){
										pot.add(against.get(i));
										indexedPot.put(cupIndex.get(against.get(i)), against.get(i));
									}
									else{
										throw new RuntimeException("Couldn't find winner of match between: " + i + " and " + against.get(i));
									}

								}
							}
							else{
								//If last round was one leg only find the winner of each match

								while (res2.next()){
									if (res2.getInt("hometeamgoals") > res2.getInt("awayteamgoals")){
										pot.add(res2.getInt("hometeamid"));
										indexedPot.put(res2.getInt("cup_index"), res2.getInt("hometeamid"));
									}
									else if (res2.getInt("hometeamgoals") < res2.getInt("awayteamgoals")){
										pot.add(res2.getInt("awayteamid"));
										indexedPot.put(res2.getInt("cup_index"), res2.getInt("awayteamid"));
									}
									else if (res2.getInt("shootoutgoalshome") > res2.getInt("shootoutgoalsaway")){
										pot.add(res2.getInt("hometeamid"));
										indexedPot.put(res2.getInt("cup_index"), res2.getInt("hometeamid"));
									}
									else if (res2.getInt("shootoutgoalshome") < res2.getInt("shootoutgoalsaway")){
										pot.add(res2.getInt("awayteamid"));
										indexedPot.put(res2.getInt("cup_index"), res2.getInt("awayteamid"));
									}
									else{
										//throw new RuntimeException("Couldn't find winner of match: " + res2.getInt("id"));
										System.out.println("Couldn't find winner of match: " + res2.getInt("id"));
									}
								}
							}
							res2.close();

							//pot is ready

						}
					}
					else{
						//First stage of a cup we get all qualified teams
						System.out.println("First round of cup");

						sql = "SELECT * FROM cups_qualified_clubs WHERE competition_stage_id = " + res.getInt("sid");
						ResultSet res2 = stmt2.executeQuery(sql);

						//Pot of qualified teams sorted by fame. Use this to create an indexed pot based on fame (seeding)
						SortedMap<Integer, Integer> potSeededByFame = new TreeMap<Integer, Integer>();

						HashMap<Integer, Integer> quals = new HashMap<Integer, Integer>();
						while (res2.next()){
							quals.put(res2.getInt("league_id"), res2.getInt("number_clubs"));
						}
						res2.close();

						for (int i : quals.keySet()){
							sql = "SELECT club_id, fame FROM league_tables t inner join clubs c ON t.club_id=c.id WHERE t.league_id = " + i + " AND season_id = (SELECT id FROM seasons WHERE id <> " + season_id + " ORDER BY number DESC LIMIT 1) ORDER BY won*3+drawn, goalsfor-goalsagainst, goalsfor LIMIT " + quals.get(i);
							res2 = stmt2.executeQuery(sql);
							int position = 1;
							while (res2.next()){
								pot.add(res2.getInt("club_id"));
								potSeededByFame.put(res2.getInt("fame"), res2.getInt("club_id"));
								position++;
							}
							res2.close();
						}

						int seed = 1;
						for (int i : potSeededByFame.keySet()){
							seededPot.put(seed, potSeededByFame.get(i));
							seed++;
						}

						//Use the four pots to seed teams
						int index = 1;
						while (potSeededByFame.size() > 0){

							//Highest seed left goes in A1
							potA1.add(potSeededByFame.remove(potSeededByFame.lastKey()));
							//Second highest seed left goes in B1
							potB1.add(potSeededByFame.remove(potSeededByFame.lastKey()));
							//Lowest seed left goes in A2
							potA2.add(potSeededByFame.remove(potSeededByFame.firstKey()));
							//Second lowest seed left goes in B2
							potB2.add(potSeededByFame.remove(potSeededByFame.firstKey()));
						}

						//Draw from the four pots to the indexedPot
						while (indexedPot.size() < pot.size() / 2){

							indexedPot.put(index, potA1.remove(r.nextInt(potA1.size())));
							index++;
							indexedPot.put(index, potA2.remove(r.nextInt(potA2.size())));
							index++;
						}
						while (indexedPot.size() < pot.size()){

							indexedPot.put(index, potB1.remove(r.nextInt(potB1.size())));
							index++;
							indexedPot.put(index, potB2.remove(r.nextInt(potB2.size())));
							index++;
						}
					}

					//------------------------
					//We now have the pot ready and can pay prize money and draw for the next round
					//------------------------

					//Pay prize money 
					if (res.getInt("leaguereputation") > 0 && res.getInt("prize_percent") > 0){
						for (int i : pot){
							System.out.println("Team " + i + " is through");

							int prizePercent = res.getInt("prize_percent");

							double amount = 0;
							switch (res.getInt("leaguereputation")){
							case 1: amount = 50000; break;
							case 2: amount = 100000; break;	
							case 3:	amount = 200000; break;	
							case 4:	amount = 500000; break;
							case 5:	amount = 1000000; break;
							case 6:	amount = 2000000; break;
							case 7:	amount = 4000000; break;
							case 8:	amount = 8000000; break;
							case 9:	amount = 16000000; break;
							case 10: amount = 24000000; break;
							}

							amount = (amount * prizePercent) / 100;
							System.out.println("prize money: " + amount);

							sql = "INSERT INTO club_incomes (amount, type, date, description, club_id) VALUES (" + amount + ", 9, now(), 'Prize money', " + i + ");";
							stmt2.execute(sql);

							sql = "INSERT INTO logs (dt, msg, type) VALUES (now(), 'Prize money paid for competition stage: " + res.getInt("prevstageid") + "', 99);";
							stmt2.execute(sql);
						}
					}


					int splitFixturesOverDays = 1;

					while (pot.size() / splitFixturesOverDays > 16)
						splitFixturesOverDays++;

					int fixturesDone = 0;
					int fixturesThisRound = 0;
					int numberOfTeams = pot.size();

					GregorianCalendar cal = new GregorianCalendar();
					cal.add(cal.DATE, res.getInt("days_to_matches"));

					//League matches should always be played on even days (day of season) so if this is an even day and days_to_matches is an even number we go one day forward 
					//to ensure cup matches are played on odd days
					if (dayOfSeason % 2 == 0 && res.getInt("days_to_matches") % 2 == 0)
						cal.add(cal.DATE, 1);

					if (res.getBoolean("knockout")){
						//Next round is a knockout round
						System.out.println("Next round is a knock out stage");

						int cupIndex = 1;
						int number = 1;

						while (indexedPot.size() > 1){
							ArrayList<Integer> teams = new ArrayList<Integer>();
							teams.add(indexedPot.remove(number));
							number++;
							teams.add(indexedPot.remove(number));
							number++;

							genCupFixtures(cupIndex, true, teams, res.getInt("lid"), cal.get(cal.YEAR), cal.get(cal.MONTH), cal.get(cal.DATE), res.getInt("matches_start_hour"), 30 * fixturesThisRound / 2, 30, res.getBoolean("two_legs"), season_id, res.getInt("sid"));
							fixturesDone += 2;
							fixturesThisRound += 2;
							cupIndex++;

							if (fixturesThisRound >= numberOfTeams / splitFixturesOverDays){
								cal.add(cal.DATE, 2);
								fixturesThisRound = 0;
							}
						}

					}
					else{
						//Next round is a group stage. Get number of teams per group - minimum 4 and evenly divided
						//Create a number of groups divisible by four or only one or two groups
						System.out.println("Next round is a group stage");
						int numberOfGroups = 1;

						while(pot.size() / (numberOfGroups * 2) >= 4){
							numberOfGroups *= 2;
						}

						//Create the groups and put random teams in them
						String[] groupNames = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N"};

						ArrayList<Integer> seeds = new ArrayList<Integer>();
						for (int key : seededPot.keySet()){
							seeds.add(seededPot.get(key));
						}

						for (int i = 0; i < numberOfGroups; i++){
							int group_id = -1;

							sql = "INSERT INTO competition_stage_groups (stage_id, season_id, group_name) VALUES (" + res.getInt("sid") + ", " + season_id + ", '" + groupNames[i] + "');";
							stmt2.execute(sql, Statement.RETURN_GENERATED_KEYS);

							ResultSet resid = stmt2.getGeneratedKeys();
							if (resid.next()){
								group_id = resid.getInt(1);
							}
							resid.close();

							int clubsPerGroup = numberOfTeams / numberOfGroups;
							ArrayList<Integer> clubs = new ArrayList<Integer>();

							for (int q = 0; q < clubsPerGroup; q++){
								int clubToAdd = (clubsPerGroup - i) * q - q + r.nextInt(clubsPerGroup - i);
								int club_id = seeds.remove(clubToAdd);
								clubs.add(club_id);
								sql = "INSERT INTO stage_group_clubs (competition_stage_group_id, club_id) VALUES (" + group_id + ", " + club_id + ");";
								stmt2.execute(sql);
							}

							//Generate fixtures for the clubs in the group
							if(clubs.size() % 2 == 0){
								genCupFixtures(-1, false, clubs, res.getInt("lid"), cal.get(cal.YEAR), cal.get(cal.MONTH), cal.get(cal.DATE), res.getInt("matches_start_hour") + fixturesThisRound / 4, 0, 30, res.getBoolean("two_legs"), season_id, res.getInt("sid"));
								fixturesDone += clubs.size();
								fixturesThisRound += clubs.size();
							}
							else{
								//TODO: gen fixtures for uneven number of teams per group
							}

							if (fixturesThisRound >= numberOfTeams / splitFixturesOverDays){
								cal.add(cal.DATE, 2);
								fixturesThisRound = 0;
							}
						}
					}

					sql = "INSERT INTO logs (dt, msg, type) VALUES (now(), 'Fixtures generated for competition stage: " + res.getInt("sid") + "', 99);";
					stmt2.execute(sql);

					//Move competition to next stage
					sql = "UPDATE leagues SET current_stage=current_stage+1 WHERE id=" + res.getInt("lid");
					stmt2.execute(sql);
				}
			}
			res.close();

			//Last day of the season we create a new season and prize money is paid for all leagues
			if (daysToLastDayOfSeason == 0){

				sql = "SELECT *, (SELECT MAX(number) FROM seasons) as season FROM leagues WHERE leaguereputation > 0 and cup=false;";
				res = stmt.executeQuery(sql);
				while (res.next()) {
					payLeaguePriceMoney(res.getInt("id"), res.getInt("season"));
				}
				res.close();

				sql = "INSERT INTO logs (dt, msg, type) VALUES (now(), 'Prize money paid for leagues', 99);";
				stmt2.execute(sql);

				createNewSeason();

				sql = "INSERT INTO logs (dt, msg, type) VALUES (now(), 'New season created', 99);";
				stmt2.execute(sql);

				//Set all leagues to first stage (only matters for cups)
				sql = "update leagues set current_stage = 0";
				stmt.execute(sql);
			}


			//Get all the cups with no more matches to play and curren_stage = the last stage and pay prize money to the winner of the last match (final)
			//Add on to curren_stage so we don't pay again tomorrow
			sql = "SELECT *, l.id as lid, s.id as sid FROM leagues l INNER JOIN competition_stages s ON l.current_stage=s.number AND s.league_id=l.id " +
					"WHERE cup=true AND current_stage = (SELECT max(number) FROM competition_stages WHERE league_id = l.id) AND " +
					"(SELECT count(*) FROM matches WHERE league_id=l.id AND status = 0) = 0;";

			res = stmt.executeQuery(sql);
			while (res.next()){
				int prizePercent = res.getInt("prize_percent");

				double amount = 0;
				switch (res.getInt("leaguereputation")){
				case 1: amount = 50000; break;
				case 2: amount = 100000; break;	
				case 3:	amount = 200000; break;	
				case 4:	amount = 500000; break;
				case 5:	amount = 1000000; break;
				case 6:	amount = 2000000; break;
				case 7:	amount = 4000000; break;
				case 8:	amount = 8000000; break;
				case 9:	amount = 16000000; break;
				case 10: amount = 24000000; break;
				}

				amount = (amount * prizePercent) / 100;
				System.out.println("prize money: " + amount);


				System.out.println(res.getInt("sid"));


				//Find final winner
				int winnerID = 0;

				sql = "SELECT * FROM matches WHERE status=2 AND league_id=" + res.getInt("lid") + " AND stage_id=" + res.getInt("sid") + " AND season_id=" + season_id + " ORDER BY matchdate LIMIT 1";
				ResultSet res2 = stmt2.executeQuery(sql);

				if (res2.next()){
					if (res2.getInt("hometeamgoals") > res2.getInt("awayteamgoals"))
						winnerID = res2.getInt("hometeamid");
					else if (res2.getInt("hometeamgoals") < res2.getInt("awayteamgoals")) {
						winnerID = res2.getInt("awayteamid");
					}
					else{
						if (res2.getInt("shootoutgoalshome") > res2.getInt("shootoutgoalsaway"))
							winnerID = res2.getInt("hometeamid");
						else
							winnerID = res2.getInt("awayteamid");
					}
				}
				res2.close();

				sql = "INSERT INTO club_incomes (amount, type, date, description, club_id) VALUES (" + amount + ", 9, now(), 'Prize money', " + winnerID + ");";
				stmt2.execute(sql);

				sql = "UPDATE leagues SET current_stage=current_stage+1 WHERE id=" + res.getInt("lid");
				stmt2.execute(sql);

				sql = "INSERT INTO logs (dt, msg, type) VALUES (now(), 'Prize money paid for final competition stage: " + res.getInt("sid") + "', 99);";
				stmt2.execute(sql);
			}
			res.close();

			//All the queries are ready. Commit
			con.commit();
			con.setAutoCommit(true);

			System.out.println("Competitions updated");

			stmt.close();
			stmt2.close();
		} catch (SQLException e) {
			try {
				con.rollback();
				System.out.println("Roll back done");
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		}
	}

	private static void genCupFixtures(int cupIndex, boolean knockout, ArrayList<Integer> index, int leagueId, int startYear, int startMonth, int startDay, int startHour, int startMinute, int minutesInterval, boolean two_legs, int season_id, int stage_id){

		Random r = new Random();
		boolean swap = false;

		try{
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			con.setAutoCommit(false);

			for (Integer j = 0; j < index.size() - 1; j++){

				ArrayList<Integer> min1 = new ArrayList<Integer>();
				ArrayList<Integer> min2 = new ArrayList<Integer>();

				for (int q = 0; q < index.size() / 2; q++){
					min1.add(startMinute + q * minutesInterval);
					min2.add(startMinute + q * minutesInterval);
				}

				for (Integer i = 0; i < index.size() / 2; i++){

					swap = !swap;

					if ((i == 0 || i == 1) && (j%2 == 0)){ //hvis det er f�rste kamp i runden byttes den kun hver anden runde
						swap = !swap;
					}

					int size = min1.size();
					Date d = new Date(startYear - 1900, startMonth, startDay + j * 2, startHour, min1.remove(r.nextInt(size)));
					String matchdate = getStringFromDate(d, true);
					System.out.println("old date: " + matchdate);

					int home = index.get(i);
					int away = index.get(index.size() - 1 - i);

					if (!swap){
					}
					else {
						home = index.get(index.size() - 1 - i);
						away = index.get(i);
					}

					//Find next date where neither team has another match 
					String teamCond = " (hometeamid=" + home + " OR awayteamid=" + home + " OR hometeamid=" + away + " OR awayteamid=" + away + ") ";
					String oddDaysCond = "";

					//Comment line below to remove odd matchday requirement
					//oddDaysCond = "date_part('day', (matchdate::date + interval '1 days')-(select max(firstday) from seasons))::int % 2 = 0 AND";

					String sql = "select count(*) as m, (select min(matchdate::date + interval '1 days') from matches m where " + oddDaysCond + " matchdate::date >= '" + getStringFromDate(d, false) + "' and (select count(*) from matches where " + teamCond + " and matchdate::date = m.matchdate::date + interval '1 days') = 0) as date from matches where " + teamCond + " and matchdate::date = '" + getStringFromDate(d, false) + "'";
					//					System.out.println(sql);

					ResultSet res = stmt.executeQuery(sql);
					if (res.next()){
						if (res.getInt("m") > 0){
							matchdate = res.getString("date");
							matchdate = matchdate.split(" ")[0];
							matchdate += " " + getStringFromDate(d, true).split(" ")[1];
							System.out.println("new date: " + matchdate);
						}
					}
					res.close();

					//It's a cup match so we need to find a winner - unless it's over two legs. Then we need to find a winner in the second leg
					String firstLegID = "NULL";
					String findWinner = "true";
					if (two_legs || !knockout)
						findWinner = "false";

					sql = "INSERT INTO matches (status, homeTeamId, awayTeamId, matchDate, stadium_id, homeTeamGoals, " +
							"awayTeamGoals, league_id, season_id, stage_id, findwinner, cup_index) values (0, " + home + ", " + away + ", '" + matchdate + "', " +
							"(SELECT stadium_id FROM clubs WHERE id = " + home + "), 0, 0, " + leagueId + ", " + season_id + ", " + stage_id + ", " + findWinner + ", " + cupIndex + ");";

					if (two_legs && knockout){
						stmt.execute(sql, Statement.RETURN_GENERATED_KEYS);

						ResultSet resid = stmt.getGeneratedKeys();
						if (resid.next()){
							firstLegID = ""+resid.getInt(1);
						}
						resid.close();
					}
					else{
						stmt.execute(sql);
					}

					System.out.println(matchdate + ":\t" + index.get(i) + " - " + index.get(index.size() - 1 - i));

					if (two_legs){
						if (knockout)
							findWinner = "true";

						size = min2.size();
						d = new Date(startYear - 1900, startMonth, startDay + j * 2 + (index.size() - 1) * 2, startHour, min2.remove(r.nextInt(size)));
						matchdate = getStringFromDate(d, true);
						System.out.println("old date: " + matchdate);

						//Find next date where neither team has another match 
						sql = "select count(*) as m, (select min(matchdate::date + interval '1 days') from matches m where " + oddDaysCond + " matchdate::date >= '" + getStringFromDate(d, false) + "' and (select count(*) from matches where " + teamCond + " and matchdate::date = m.matchdate::date + interval '1 days') = 0) as date from matches where " + teamCond + " and matchdate::date = '" + getStringFromDate(d, false) + "'";
						//						System.out.println(sql);
						res = stmt.executeQuery(sql);
						if (res.next()){
							if (res.getInt("m") > 0){
								matchdate = res.getString("date");
								matchdate = matchdate.split(" ")[0];
								matchdate += " " + getStringFromDate(d, true).split(" ")[1];
								System.out.println("new date: " + matchdate);
							}
						}
						res.close();

						System.out.println(matchdate + ":\t" + index.get(index.size() - 1 - i) + " - " + index.get(i));

						sql = "INSERT INTO matches (status, homeTeamId, awayTeamId, matchDate, stadium_id, homeTeamGoals, " +
								"awayTeamGoals, league_id, season_id, stage_id, findwinner, firstleg, cup_index) values (0, " + away + ", " + home + ", '" + matchdate + "', " +
								"(SELECT stadium_id FROM clubs WHERE id = " + away + "), 0, 0, " + leagueId + ", " + season_id + ", " + stage_id + ", " + findWinner + ", " + firstLegID + ", " + cupIndex + ");";

						stmt.execute(sql);
					}
				}
				System.out.println();
				index.add(1, index.remove(index.size() - 1));
			}

			//All the queries are ready. Commit
			con.commit();
			con.setAutoCommit(true);
		}
		catch (Exception e) {
			try {
				con.rollback();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		}
	}

	public static int leaguePriceMoney(int leagueRep, int clubPos){
		//Get league rep
		//leagueRep = 0;
		double price = 0;
		switch (leagueRep){
		case 0:	price = 25000; break;
		case 1: price = 50000; break;
		case 2: price = 100000; break;	
		case 3:	price = 200000;	break;	
		case 4:	price = 500000;	break;
		case 5:	price = 1000000; break;
		case 6:	price = 2000000; break;
		case 7:	price = 4000000; break;
		case 8:	price = 8000000; break;
		case 9:	price = 16000000; break;
		case 10: price = 24000000; break;
		}
		//		System.out.println("League Price Money: " + price);
		if(clubPos != 1){
			//pos er det antal procent der skal sk�res fra f�rstepr�mien
			double pos = 20;
			if(clubPos == 3) pos += 10;
			else if(clubPos == 4) pos += 15;
			else if(clubPos >= 5 && clubPos <= 10) pos += (clubPos -4) * 4 + 15;
			else if(clubPos >= 11) pos += (clubPos - 10) * 2.5 + 39;
			System.out.println("Pos: " + pos);
			price = price - ((price/100) * pos);
		}
		return (int)price;
	}

	public static void writeClubGraphs(){
		try {
			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			String sql = "INSERT INTO club_graphs (created, club_id, money, fame) (SELECT now(), id, money, fame FROM clubs);";
			stmt.execute(sql);

			System.out.println("Club graphs written");
			stmt.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void payClubMaintenance(){
		try {
			con.setAutoCommit(false);
			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			String sql = "UPDATE clubs c SET money = money - (trainingfacc*trainingfacc*100+(random()*5*trainingfacc)::int + (SELECT ((|/ terraces) + random() * |/terraces * 0.05) * 2 + ((|/ seats) + random() * |/seats * 0.05) * 3 FROM stadiums WHERE id = c.stadium_id)::int);";
			//stmt.execute(sql);
			sql = "insert into club_expenses (amount, type, date, description, club_id) (SELECT trainingfacc*trainingfacc*100+(random()*5*trainingfacc)::int, 6, now(),'Training facilities maintenance', id FROM clubs);";
			stmt.execute(sql);
			sql = "insert into club_expenses (amount, type, date, description, club_id) (SELECT ((|/ terraces) + random() * |/terraces * 0.05) * 2 + ((|/ seats) + random() * |/seats * 0.05) * 3, 6, now(),'Stadium maintenance', c.id FROM clubs c INNER JOIN stadiums s ON c.stadium_id=s.id);";
			stmt.execute(sql);
			System.out.println("Club maintenance paid");
			stmt.close();
			con.commit();
			con.setAutoCommit(true);
		} catch (SQLException e) {
			try {
				con.rollback();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		}
	}

	public static void subOn(int match_id, int minute, int club_id, int player_id, int subNo){
		Statement stmt;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			String sql = "UPDATE match_lineups SET subon" + subNo + " = " + player_id + ", subtime" + subNo + " = " + minute + " WHERE match_id=" + match_id + " AND club_id=" + club_id + ";";
			stmt.execute(sql);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void subOff(int match_id, int club_id, int player_id, int subNo){
		Statement stmt;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			String sql = "UPDATE match_lineups SET suboff" + subNo + " = " + player_id + " WHERE match_id=" + match_id + " AND club_id=" + club_id + ";";
			stmt.execute(sql);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void createMatchEvent(int match_id, int club_id, int minute, int player_id, MatchEventType type){
		Statement stmt;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			String person_id = ""+player_id;
			if (player_id == -1)
				person_id = "null";

			String sql = "INSERT INTO match_events (match_id, club_id, person_id, matchminute, type) VALUES (" + match_id + ", " + club_id + ", " + person_id + ", " + minute + ", " + type.ordinal() +  ");";
			stmt.execute(sql);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void addPlayerPoints(){
		int id = 0;
		try {
			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			String sql = "select id, trainingfacc from clubs";
			ResultSet facc = stmt.executeQuery(sql);

			HashMap<Integer, Integer> clubFacc = new HashMap<Integer, Integer>();
			while (facc.next()){
				clubFacc.put(facc.getInt("id"), facc.getInt("trainingfacc"));
			}

			sql = "select *, " +
			"(select club_id from contracts where enddate > now() and person_id=p.id and acceptedbyplayer='t') as clubid, " +
			"(select count(*) from person_trait where person_id=p.id and trait_id=1) as yg, " +
			"(select count(*) from person_trait where person_id=p.id and trait_id=2) as lb " +
			"from persons p WHERE id NOT IN (SELECT person_id FROM player_potentials) order by id asc;";
			ResultSet res = stmt.executeQuery(sql);

			double pp = 0;
			int alder = 0;

			double pppre = 0;//spillerens pp f�r udregningen
			Random ran = new Random();
			while (res.next()){ //G� GENNEM HVER SPILLER

				Player player = new Player();
				player.setId(res.getInt(1));
				player.acceleration = res.getDouble("acceleration");
				player.topSpeed = res.getDouble("topspeed");
				player.agility = res.getDouble("agility");
				player.strength = res.getDouble("strength");
				player.jumping = res.getDouble("jumping");
				player.reaction = res.getDouble("reaction");
				player.stamina = res.getDouble("stamina");
				player.dribbling = res.getDouble("dribbling");
				player.shooting = res.getDouble("shooting");
				player.shotpower = res.getDouble("shotpower");
				player.passing = res.getDouble("passing");
				player.technique = res.getDouble("technique");
				player.vision = res.getDouble("vision");
				player.marking = res.getDouble("marking");
				player.tackling = res.getDouble("tackling");
				player.heading = res.getDouble("heading");
				player.commandOfArea = res.getDouble("commandofarea");
				player.handling = res.getDouble("handling");
				player.rushingout = res.getDouble("rushingout");
				player.shotstopping = res.getDouble("shotstopping");
				
				
				boolean youngGun = false;
				if (res.getInt("yg") > 0) youngGun = true;
				boolean lateBloomer = false;
				if (res.getInt("lb") > 0) lateBloomer = true;
				int clubid = res.getInt("clubid");

				alder = res.getInt("age");
				alder /= 365;
				id = res.getInt(1);
				pppre = res.getDouble(27);
				pp = 0;


				//				if (youngGun)
				//					System.out.println(id + " - yg.");
				//				else if (lateBloomer)
				//					System.out.println(id + " - lb.");

				//Der er flere trin vi skal gennem for at finde det antal PP spilleren skal have
				//1: NATURLIG UDVIKLING (SPILLERENS ALDER) 
				if (alder < 17){
					pp += 0.8;
					if (youngGun){
						pp += 0.5;
					}
					else if (lateBloomer){
						pp -= 0.5;
					}
				}
				else if (alder < 20){
					pp += 0.6;
				}
				else if (alder < 24){
					pp += 0.2;	
					if (youngGun){
						pp -= 0.7;
					}
					else if (lateBloomer){
						pp += 0.7;
					}
				}
				else if (alder < 28){
					pp += 0;
				}
				else if (alder < 32){
					pp -= 1;
				}
				else if (alder < 36){
					pp -= 1.5;
				}
				else{
					pp -= 2.5;
				}

				//hvis pp er negativ, skal vi have en algoritme der selv sk�rer ned p� spillerens egenskaber
				//2:FJERN ATTRIBUTE POINTS FRA SPILLEREN HVIS HANS NATURLIGE UDVIKLING ER I MINUS
				if (pp < 0){ 
					removePPFromNaturalDevelopment(pp, player);
					pp = 0; //N�r vi har taget de PP fra atributterne som vi skal, s� skal de ikke tages igen.
				}

				//				if (youngGun || lateBloomer)
				//					System.out.println(pp + " efter alder.");
				//3: TR�NINGSFACILITETER  (Skal udvides) Indtil videre g�r vi ud fra et mellemgodt tr�ningsanl�g (5 ud af 10)

				int r;

				//				r = ran.nextInt(4) + 1;
				//				pp += (double)r / 10.0; 

				int lvl = 0;

				if (clubid > 0)
					lvl = clubFacc.get(clubid);

				//level 1:
				if (lvl == 1){
					r = ran.nextInt(4) +1;
					pp += (double)r / 10.0; 
				}				
				//level 2:
				if (lvl == 2){
					r = ran.nextInt(5) +1;
					pp += (double)r / 10.0; 
				}				
				//level 3:
				if (lvl == 3){
					r = ran.nextInt(6) +1;
					pp += (double)r / 10.0; 
				}
				//level 4:
				if (lvl == 4){
					r = ran.nextInt(7) +1;
					if (r>6) r = 6;
					pp += (double)r / 10.0; 
				}
				//level 5:
				if (lvl == 5){
					r = ran.nextInt(8) +1;
					if (r>6) r = 6;
					pp += (double)r / 10.0; 
				}
				//level 6:
				if (lvl == 6){
					r = ran.nextInt(9) +1;
					if (r>6) r = 6;
					pp += (double)r / 10.0; 
				}
				//level 7:
				if (lvl == 7){
					r = ran.nextInt(10) +1;
					if (r>6) r = 6;
					pp += (double)r / 10.0;
				}
				//level 8:
				if (lvl == 8){
					r = ran.nextInt(10) +1;
					if (r>7) r = 7;
					pp += (double)r / 10.0;
				}
				//level 9:
				if (lvl == 9){
					r = ran.nextInt(11) +1;
					if (r>7) r = 7;
					pp += (double)r / 10.0; 
				}
				//level 10:
				if (lvl == 10){
					r = ran.nextInt(12) +1;
					if (r>7) r = 7;
					pp += (double)r / 10.0; 
				}
				r = ran.nextInt(3)+1;//var f�rst ment som en bonus til dem der ikke spillede kampe. men nu blot en bonus til alle
				pp += (double)r / 15.0; 

				//				if (youngGun || lateBloomer)
				//					System.out.println(pp + " efter tr�ning.");

				//4: Training boost....todo (Enkelte spillere v�lges ud til at f� et tr�ningsboost hvor de f�r markant flere pp hver dag i et tidsinterval)


				//5: OPDATER SPILLEREN med de nye pp


				if (pp < 0){
					
				}
				else{
					pp = Round(pp, 2);
					pppre += pp;
					Statement stmt2 = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
					String sql2 = "UPDATE persons SET playerpoints = " + pppre + "WHERE id = " + id + ";";
					stmt2.execute(sql2);
					stmt2.close();
				}

			}
			System.out.println("PP added");

			stmt.close();
			res.close();
		} catch (SQLException e) {
			e.printStackTrace();
			System.out.println(e.getStackTrace());
			System.out.println(e.getMessage());
			System.out.println("Last id: " + id);
		}
	}

	public static void setPlayerAttributes(Player player){
		String sql = "UPDATE persons SET " + 
	"acceleration=" + (Math.round(player.acceleration * 100) / 100.0) + ", " +
	"topSpeed=" + (Math.round(player.topSpeed * 100) / 100.0) + ", " + 
	"agility=" + (Math.round(player.agility * 100) / 100.0) + ", " + 
	"strength=" + (Math.round(player.strength * 100) / 100.0) + ", " + 
	"jumping=" + (Math.round(player.jumping * 100) / 100.0) + ", " + 
	"reaction=" + (Math.round(player.reaction * 100) / 100.0) + ", " + 
	"stamina=" + (Math.round(player.stamina * 100) / 100.0) + ", " + 
	"dribbling=" + (Math.round(player.dribbling * 100) / 100.0) + ", " + 
	"shooting=" + (Math.round(player.shooting * 100) / 100.0) + ", " + 
	"shotpower=" + (Math.round(player.shotpower * 100) / 100.0) + ", " + 
	"passing=" + (Math.round(player.passing * 100) / 100.0) + ", " + 
	"technique=" + (Math.round(player.technique * 100) / 100.0) + ", " + 
	"vision=" + (Math.round(player.vision * 100) / 100.0) + ", " + 
	"marking=" + (Math.round(player.marking * 100) / 100.0) + ", " + 
	"tackling=" + (Math.round(player.tackling * 100) / 100.0) + ", " + 
	"heading=" + (Math.round(player.heading * 100) / 100.0) + ", " + 
	"commandOfArea=" + (Math.round(player.commandOfArea * 100) / 100.0) + ", " + 
	"handling=" + (Math.round(player.handling * 100) / 100.0) + ", " +
	"rushingout=" + (Math.round(player.rushingout * 100) / 100.0) + ", " + 
	"shotstopping=" + (Math.round(player.shotstopping * 100) / 100.0) + " " +
	" WHERE id=" + player.getId();
	
		Statement stmt;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			stmt.execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public static void removePPFromNaturalDevelopment(double pp, Player player){
		if(pp < 0){
			double ppLeft = Math.round(pp * 100) / -100.0;

			Hashtable<Player.Attributes, Integer> table = new Hashtable<Player.Attributes, Integer>();
			table.put(Player.Attributes.acceleration, 15);
			table.put(Player.Attributes.topSpeed, 15);
			table.put(Player.Attributes.agility, 15);
			table.put(Player.Attributes.strength, 5);
			table.put(Player.Attributes.jumping, 5);
			table.put(Player.Attributes.reaction, 5);
			table.put(Player.Attributes.stamina, 27);
			table.put(Player.Attributes.dribbling, 1);
			table.put(Player.Attributes.shooting, 1);
			table.put(Player.Attributes.shotpower, 1);
			table.put(Player.Attributes.passing, 1);
			table.put(Player.Attributes.technique, 1);
			table.put(Player.Attributes.vision, 1);
			table.put(Player.Attributes.marking, 1);
			table.put(Player.Attributes.tackling, 1);
			table.put(Player.Attributes.heading, 1);
			table.put(Player.Attributes.commandOfArea, 1);
			table.put(Player.Attributes.handling, 1);
			table.put(Player.Attributes.rushingout, 1);
			table.put(Player.Attributes.shotstopping, 1);

			//Total weights in the attributes we can decrease
			int totalPoints = 0;
			for (Integer i : table.values()){
				totalPoints += i;
			}
			
			//Safety first. Max amount of times we'll try to decrease the players stats (in case we end up in an otherwise infite loop)
			int maxRuns = 1000;
			while(ppLeft > 0 && totalPoints > 0 && maxRuns > 0){
				maxRuns--;
				
				int nr = ran.nextInt(totalPoints);
				
				int weightSum = 0;
				Player.Attributes remove = null;
				
				for (Map.Entry<Player.Attributes, Integer> attribute : table.entrySet()){
					weightSum += attribute.getValue();
					
					if (nr < weightSum){
						switch (attribute.getKey()){
							case acceleration:
								if(player.acceleration >= 10.01){
									player.acceleration -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.acceleration) / 10;
								}
								else{
									remove = Player.Attributes.acceleration;
								}
								break;
								
							case topSpeed:
								if(player.topSpeed >= 10.01){
									player.topSpeed -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.topSpeed) / 10;
								}
								else{
									remove = Player.Attributes.topSpeed;
								}
								break;
								
							case agility:
								if(player.agility >= 10.01){
									player.agility -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.agility) / 10;
								}
								else{
									remove = Player.Attributes.agility;
								}
								break;
								
							case strength:
								if(player.strength >= 10.01){
									player.strength -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.strength) / 10;
								}
								else{
									remove = Player.Attributes.strength;
								}
								break;
								
							case jumping:
								if(player.jumping >= 10.01){
									player.jumping -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.jumping) / 10;
								}
								else{
									remove = Player.Attributes.jumping;
								}
								break;
								
							case reaction:
								if(player.reaction >= 10.01){
									player.reaction -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.reaction) / 10;
								}
								else{
									remove = Player.Attributes.reaction;
								}
								break;
								
							case dribbling:
								if(player.dribbling >= 10.01){
									player.dribbling -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.dribbling) / 10;
								}
								else{
									remove = Player.Attributes.dribbling;
								}
								break;
								
							case shooting:
								if(player.shooting >= 10.01){
									player.shooting -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.shooting) / 10;
								}
								else{
									remove = Player.Attributes.shooting;
								}
								break;
								
							case shotpower:
								if(player.shotpower >= 10.01){
									player.shotpower -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.shotpower) / 10;
								}
								else{
									remove = Player.Attributes.shotpower;
								}
								break;
								
							case passing:
								if(player.passing >= 10.01){
									player.passing -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.passing) / 10;
								}
								else{
									remove = Player.Attributes.passing;
								}
								break;
								
							case technique:
								if(player.technique >= 10.01){
									player.technique -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.technique) / 10;
								}
								else{
									remove = Player.Attributes.technique;
								}
								break;
								
							case vision:
								if(player.vision >= 10.01){
									player.vision -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.vision) / 10;
								}
								else{
									remove = Player.Attributes.vision;
								}
								break;
								
							case marking:
								if(player.marking >= 10.01){
									player.marking -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.marking) / 10;
								}
								else{
									remove = Player.Attributes.marking;
								}
								break;
								
							case tackling:
								if(player.tackling >= 10.01){
									player.tackling -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.tackling) / 10;
								}
								else{
									remove = Player.Attributes.tackling;
								}
								break;
								
							case heading:
								if(player.heading >= 10.01){
									player.heading -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.heading) / 10;
								}
								else{
									remove = Player.Attributes.heading;
								}
								break;
								
							case commandOfArea:
								if(player.commandOfArea >= 10.01){
									player.commandOfArea -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.commandOfArea) / 10;
								}
								else{
									remove = Player.Attributes.commandOfArea;
								}
								break;
								
							case handling:
								if(player.handling >= 10.01){
									player.handling -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.handling) / 10;
								}
								else{
									remove = Player.Attributes.handling;
								}
								break;
								
							case rushingout:
								if(player.rushingout >= 10.01){
									player.rushingout -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.rushingout) / 10;
								}
								else{
									remove = Player.Attributes.rushingout;
								}
								break;
								
							case shotstopping:
								if(player.shotstopping >= 10.01){
									player.shotstopping -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.shotstopping) / 10;
								}
								else{
									remove = Player.Attributes.shotstopping;
								}
								break;
								
							default:
							
								if(player.stamina >= 10.01){
									player.stamina -= 0.01;
									ppLeft -= TrainingRegime.CostToIncreaseAbility(player.stamina) / 10;
								}
								else{
									remove = Player.Attributes.stamina;
								}
								break;	
							
						}
					}
				}
				if (remove != null){
					totalPoints -= table.get(remove);
					table.remove(remove);
				}
			}
			if (totalPoints > 0)
				setPlayerAttributes(player);
		}	
	}

	public static void DailyPPSpend(){
		String sql = "SELECT p.id as person_id, p.playerpoints, t.* FROM persons p INNER JOIN training_regimes t on p.training_regime_id=t.id WHERE playerpoints >= 30;";

		try {
			ResultSet res = stmt.executeQuery(sql);
			Statement stmt2 = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			Statement stmt3 = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			while (res.next()){//G� GENNEM HVER SPILLER
				ResultSet resPlayer = stmt3.executeQuery("SELECT * FROM persons WHERE id=" + res.getInt("person_id"));
				if (resPlayer.next()){
					TrainingRegime tr = new TrainingRegime();

					tr.person_id = res.getInt("person_id");
					tr._acceleration = res.getInt("acceleration");
					tr._topspeed = tr._acceleration + res.getInt("topspeed");
					tr._dribbling = tr._topspeed + res.getInt("dribbling");
					tr._marking = tr._dribbling + res.getInt("marking");
					tr._strength = tr._marking + res.getInt("strength");
					tr._tackling = tr._strength + res.getInt("tackling");
					tr._agility = tr._tackling + res.getInt("agility");
					tr._reaction = tr._agility + res.getInt("reaction");
					tr._shooting = tr._reaction + res.getInt("shooting");
					tr._shotpower = tr._shooting + res.getInt("shotpower");
					tr._vision = tr._shotpower + res.getInt("vision");
					tr._passing = tr._vision + res.getInt("passing");
					tr._technique = tr._passing + res.getInt("technique");
					tr._jumping = tr._technique + res.getInt("jumping");
					tr._stamina = tr._jumping + res.getInt("stamina");
					tr._heading = tr._stamina + res.getInt("heading");
					tr._handling = tr._heading + res.getInt("handling");
					tr._commandofarea = tr._handling + res.getInt("commandofarea");
					tr._shotstopping = tr._commandofarea + res.getInt("shotstopping");
					tr._rushingout = tr._shotstopping + res.getInt("rushingout");

					//Write it to the db
					try {
						String save = tr.generateSQLFromTraining(res.getDouble("playerpoints"), resPlayer);
						//					System.out.println(save);
						stmt2.execute(save);
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}
				resPlayer.close();
			}

			stmt2.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("Player points spent");
	}


	public static double Round(double Rval, int Rpl) {
		double p = (double)Math.pow(10,Rpl);
		Rval = Rval * p;
		double tmp = Math.round(Rval);
		return (double)tmp/p;
	}

	public static void createMatch(int minsFromNow){

		try {
			openConnection();

			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			Date d = new Date();

			String date = (d.getYear() + 1900) + "-";

			if (d.getMonth() < 9)
				date += "0" + (d.getMonth() + 1);
			else
				date += "" + (d.getMonth() + 1);

			if (d.getDate() < 10)
				date += "-0" + d.getDate();
			else
				date += "-" + d.getDate();

			if (d.getHours() < 10)
				date += " 0" + d.getHours();
			else
				date += " " + d.getHours();	

			if (d.getMinutes() + minsFromNow < 10)
				date += ":0" + (d.getMinutes() + minsFromNow);
			else
				date += ":" + (d.getMinutes() + minsFromNow);	

			if (d.getSeconds() < 10)
				date += ":0" + d.getSeconds();
			else
				date += ":" + d.getSeconds();			

			String sql = "INSERT INTO matches (status, homeTeamId, awayTeamId, matchDate, stadium_id, homeTeamGoals, " +
					"awayTeamGoals, league_id, findwinner) values (0, 11, 12, '" + date + "', 11, 0, 0, 3, 't');";

			stmt.execute(sql);
			stmt.close();

		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void fullEnergy(){
		Statement stmt;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			String sql = "UPDATE persons SET energy=99;";
			stmt.execute(sql);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void clearSetPieceTakers(){
		Statement stmt;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			String sql = "UPDATE teamtactics SET freekicklong=-1, freekickshort=-1;";
			stmt.execute(sql);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static boolean findWinner(Match match) throws SQLException{

		Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

		String sql = "SELECT findwinner, firstleg, (SELECT hometeamgoals FROM matches WHERE id=m.firstleg) as firstleghome, (SELECT awayteamgoals FROM matches WHERE id=m.firstleg) as firstlegaway FROM matches m WHERE id=" + match.getMatchId()+ ";";
		ResultSet res = stmt.executeQuery(sql);

		boolean result = false;

		if (res.next())
			result = res.getBoolean("findWinner");


		match.setMustFindWinner(result);

		System.out.println("findwinner: " + match.isMustFindWinner());

		if (match.isMustFindWinner() && res.getInt("firstleg") > 0){
			System.out.println(res.getInt("firstleghome"));
			System.out.println(res.getInt("firstlegaway"));
			match.setFirstMatchGoalsA(res.getInt("firstlegaway"));
			match.setFirstMatchGoalsB(res.getInt("firstleghome"));
		}

		res.close();
		stmt.close();

		return result;
	}

	public static int getNextMatchId() throws SQLException{

		Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

		String sql = "SELECT id, status FROM matches WHERE status=0 ORDER BY matchDate ASC;";
		ResultSet res = stmt.executeQuery(sql);

		int result = 0;

		if (res.next())
			result = res.getInt("id");

		res.close();
		stmt.close();

		return result;
	}

	public static int getLeagueId(int matchId) {

		Statement stmt;
		int result = 0;

		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			String sql = "SELECT league_id FROM matches WHERE id=" + matchId + ";";
			ResultSet res = stmt.executeQuery(sql);

			if (res.next())
				result = res.getInt(1);
			else 
				result = 0;

			res.close();
			stmt.close();

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return result;
	}

	public static int getMatchRep(int matchId){
		int matchRep = 0;

		try {
			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			String sql = "SELECT leaguereputation FROM leagues WHERE id=(SELECT league_id FROM matches WHERE id=" + matchId + ")";
			ResultSet res = stmt.executeQuery(sql);

			if (res.next())
				matchRep = res.getInt(1);

			res.close();
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return matchRep;
	}

	public static void closeMatch(Match match, int home, int away, Team homeTeam, Team awayTeam){

		try {
			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			//Update match info
			String sql = "UPDATE matches SET status=2, homeTeamGoals=" + 
					home + ", awayTeamGoals=" + away + " WHERE id=" + match.getMatchId() + ";";
			stmt.execute(sql);


			if (match.getMatchrep() > 0){

				//Update league table
				sql = "UPDATE league_tables SET matches=matches+1, goalsfor=goalsfor + " + home + ", goalsagainst=goalsagainst + " + away;
				if (home > away) 
					sql += ", won=won + 1 ";
				else if (home == away)
					sql += ", drawn=drawn + 1 ";

				sql += "WHERE club_id = " + homeTeam.getId() + " AND season_id = (SELECT id FROM seasons WHERE firstday <= current_date AND lastday >= current_date) AND league_id = " + match.getLeagueId();
				stmt.execute(sql);

				sql = "UPDATE league_tables SET matches=matches+1, goalsfor=goalsfor + " + away + ", goalsagainst=goalsagainst + " + home;
				if (away > home) 
					sql += ", won=won + 1 ";
				else if (home == away)
					sql += ", drawn=drawn + 1 ";

				sql += "WHERE club_id = " + awayTeam.getId() + " AND season_id = (SELECT id FROM seasons WHERE firstday <= current_date AND lastday >= current_date) AND league_id = " + match.getLeagueId();
				stmt.execute(sql);

				//Update winning / losing streak if it's a league match
				if (!match.isCup()){
					try{

						int loserID = -1;
						int winnerID = -1;

						if (home > away){
							winnerID = homeTeam.getId();
							homeTeam.getRecords().currWinningStreak++;
							homeTeam.getRecords().currUnbeatenRun++;
							loserID = awayTeam.getId();
							awayTeam.getRecords().currLosingStreak++;
						}
						else if (home < away){
							loserID = homeTeam.getId();
							homeTeam.getRecords().currLosingStreak++;
							winnerID = awayTeam.getId();
							awayTeam.getRecords().currWinningStreak++;
							awayTeam.getRecords().currUnbeatenRun++;
						}
						else{
							awayTeam.getRecords().currUnbeatenRun++;
							homeTeam.getRecords().currUnbeatenRun++;

							writeRecord(homeTeam.getId(), 0, "", ClubRecordType.CurrLosingStreak, match.getLeagueId(), match.getSeasonId());
							writeRecord(awayTeam.getId(), 0, "", ClubRecordType.CurrLosingStreak, match.getLeagueId(), match.getSeasonId());

							writeRecord(homeTeam.getId(), homeTeam.getRecords().currUnbeatenRun, "", ClubRecordType.CurrUnbeatenRun, match.getLeagueId(), match.getSeasonId());
							writeRecord(awayTeam.getId(), awayTeam.getRecords().currUnbeatenRun, "", ClubRecordType.CurrUnbeatenRun, match.getLeagueId(), match.getSeasonId());
						}

						if (home != away){

							writeRecord(homeTeam.getId(), homeTeam.getRecords().currUnbeatenRun, "", ClubRecordType.CurrUnbeatenRun, match.getLeagueId(), match.getSeasonId());
							writeRecord(awayTeam.getId(), awayTeam.getRecords().currUnbeatenRun, "", ClubRecordType.CurrUnbeatenRun, match.getLeagueId(), match.getSeasonId());

							writeRecord(homeTeam.getId(), homeTeam.getRecords().currLosingStreak, "", ClubRecordType.CurrLosingStreak, match.getLeagueId(), match.getSeasonId());
							writeRecord(awayTeam.getId(), awayTeam.getRecords().currLosingStreak, "", ClubRecordType.CurrLosingStreak, match.getLeagueId(), match.getSeasonId());

							writeRecord(homeTeam.getId(), homeTeam.getRecords().currWinningStreak, "", ClubRecordType.CurrWinningStreak, match.getLeagueId(), match.getSeasonId());
							writeRecord(awayTeam.getId(), awayTeam.getRecords().currWinningStreak, "", ClubRecordType.CurrWinningStreak, match.getLeagueId(), match.getSeasonId());
						}

						checkRecords(homeTeam, match, true, awayTeam.getName());
						checkRecords(awayTeam, match, false, homeTeam.getName());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}


				//Update fame
				if (away > home){
					sql = "UPDATE clubs SET fame = fame + " + (away * awayTeam.getLeagueReputation()) + " + ((SELECT fame FROM clubs WHERE id=" + homeTeam.getId() + ") / fame * 40 * " + awayTeam.getLeagueReputation() + ") WHERE id=" + awayTeam.getId() + ";";
					stmt.execute(sql);
					sql = "UPDATE clubs SET fame = fame + " + (home * homeTeam.getLeagueReputation()) + " + (fame / (SELECT fame FROM clubs WHERE id=" + awayTeam.getId() + ") * -40 * " + homeTeam.getLeagueReputation() + ") WHERE id=" + homeTeam.getId() + ";";
					stmt.execute(sql);
				}
				else if (home > away){
					sql = "UPDATE clubs SET fame = fame + " + (away * awayTeam.getLeagueReputation()) + " + (fame / (SELECT fame FROM clubs WHERE id=" + homeTeam.getId() + ") * -40 * " + awayTeam.getLeagueReputation() + ") WHERE id=" + awayTeam.getId() + ";";
					stmt.execute(sql);
					sql = "UPDATE clubs SET fame = fame + " + (home * homeTeam.getLeagueReputation()) + " + ((SELECT fame FROM clubs WHERE id=" + awayTeam.getId() + ") / fame * 40 * " + homeTeam.getLeagueReputation() + ") WHERE id=" + homeTeam.getId() + ";";
					stmt.execute(sql);
				}
				else{
					sql = "UPDATE clubs SET fame = fame + " + (away * awayTeam.getLeagueReputation()) + " + ((SELECT fame FROM clubs WHERE id=" + homeTeam.getId() + ") / fame * 20 * " + awayTeam.getLeagueReputation() + ") WHERE id=" + awayTeam.getId() + ";";
					stmt.execute(sql);
					sql = "UPDATE clubs SET fame = fame + " + (home * homeTeam.getLeagueReputation()) + " + ((SELECT fame FROM clubs WHERE id=" + awayTeam.getId() + ") / fame * 20 * " + homeTeam.getLeagueReputation() + ") WHERE id=" + homeTeam.getId() + ";";
					stmt.execute(sql);
				}

				//Update morale
				updateMatchMorale(home, away, homeTeam, awayTeam);

				//Update energy
				for (Player p : homeTeam.getPlayers()){
					sql = "UPDATE persons SET energy =" + Math.round(p.getEnergy()) + " WHERE id=" + p.getId() + ";";
					stmt.execute(sql);
				}
				for (Player p : homeTeam.getUsedSubs()){
					sql = "UPDATE persons SET energy =" + Math.round(p.getEnergy()) + " WHERE id=" + p.getId() + ";";
					stmt.execute(sql);
				}
				for (Player p : awayTeam.getPlayers()){
					sql = "UPDATE persons SET energy =" + Math.round(p.getEnergy()) + " WHERE id=" + p.getId() + ";";
					stmt.execute(sql);
				}
				for (Player p : awayTeam.getUsedSubs()){
					sql = "UPDATE persons SET energy =" + Math.round(p.getEnergy()) + " WHERE id=" + p.getId() + ";";
					stmt.execute(sql);
				}
			}
			stmt.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void writeRecord(int club_id, int value, String description, ClubRecordType type, int league_id, int season_id){

		try {
			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			//Update the record
			String sql = "UPDATE club_records SET value = " + value + ", description = '" + description + "', league_id = " + league_id + ", season_id = " + season_id + ", date = CURRENT_DATE WHERE type_id = " + type.ordinal() + " AND club_id = " + club_id;
			int rows = stmt.executeUpdate(sql);


			//If nothing was updated it's because there is no current record of that type for this club. Insert a new one
			if (rows == 0){
				sql = "INSERT INTO club_records (club_id, value, date, description, type_id, league_id, season_id) VALUES " +
						"(" + club_id + ", " + value + ", CURRENT_DATE, '" + description + "', " + type.ordinal() + ", " + league_id + ", " + season_id + ");";

				stmt.execute(sql);
			}

			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private static void checkRecords(Team t, Match m, boolean home, String oppTeam){

		try {
			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			ClubRecords r = t.getRecords();

			if (r.currLosingStreak > r.losingStreak){
				writeRecord(t.getId(), r.currLosingStreak, "", ClubRecordType.LosingStreak, m.getLeagueId(), m.getSeasonId());
			}
			if (r.currUnbeatenRun  > r.unbeatenRun){
				writeRecord(t.getId(), r.currUnbeatenRun, "", ClubRecordType.UnbeatenRun, m.getLeagueId(), m.getSeasonId());
			}
			if (r.currWinningStreak  > r.winningStreak){
				writeRecord(t.getId(), r.currWinningStreak, "", ClubRecordType.WinningStreak, m.getLeagueId(), m.getSeasonId());
			}

			if (home && m.attendance > r.highestAttendance){
				writeRecord(t.getId(), m.attendance, "against " + oppTeam, ClubRecordType.HighestAttendance, m.getLeagueId(), m.getSeasonId());
			}
			else if (home && (m.attendance < r.lowestAttendance || r.lowestAttendance == -1)){
				writeRecord(t.getId(), m.attendance, "against " + oppTeam, ClubRecordType.LowestAttendance, m.getLeagueId(), m.getSeasonId());
			}

			ArrayList<Player> players = new ArrayList<Player>();

			players.addAll(t.getPlayers());
			players.addAll(t.getUsedSubs());

			for (Player p : players){
				if (p.getPlayerMatchStats().goals > r.goalsInAMatch){
					r.goalsInAMatch = p.getPlayerMatchStats().goals;
					writeRecord(t.getId(), r.goalsInAMatch, p.getFirstname() + " " + p.getLastname() + " against " + oppTeam, ClubRecordType.GoalsInMatch, m.getLeagueId(), m.getSeasonId());
				}

				if (p.getAge() < r.youngestPlayer || r.youngestPlayer == -1){
					r.youngestPlayer = p.getAge();
					writeRecord(t.getId(), r.youngestPlayer, p.getFirstname() + " " + p.getLastname() + " against " + oppTeam, ClubRecordType.YoungestPlayer, m.getLeagueId(), m.getSeasonId());
				}
				if (p.getAge() > r.oldestPlayer){
					r.oldestPlayer = p.getAge();
					writeRecord(t.getId(), r.oldestPlayer, p.getFirstname() + " " + p.getLastname() + " against " + oppTeam, ClubRecordType.OldestPlayer, m.getLeagueId(), m.getSeasonId());
				}
			}


			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void updatePlayerTactics(Player p){
		try {
			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			String sql = "SELECT * FROM match_playertactics WHERE person_id=" + p.getId();
			ResultSet res = stmt.executeQuery(sql);

			if (res.next()){
				p.setAggression(res.getByte("aggression"));
				p.setClosingdown(res.getByte("closingdown"));
				p.setMentality(res.getByte("mentality"));
				p.setLongshots(res.getByte("longshots"));
				p.setThroughballs(res.getByte("throughballs"));
				p.setDribble(res.getByte("dribble"));
				p.setRuns(res.getByte("runs"));
				p.setCrossball(res.getByte("crossball"));
				p.setPressing(res.getByte("tightmarking"));
				p.setShortLongPassing(res.getByte("passing")); 
				p.setForwardOnSetpieces(res.getBoolean("forwardOnSetPieces"));
			}

			res.close();
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		System.out.println("Player tactics updated for " + p.getLastname() + ". Mentality: " + p.getMentality());
	}

	public static void updateTeamRoles(Team team){
		try {
			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			String sql = "SELECT * FROM match_teamtactics WHERE club_id=" + team.getId();
			ResultSet res = stmt.executeQuery(sql);

			team.setpCaptain(null);
			team.setpThrowinRight(null);
			team.setpThrowinLeft(null);
			team.setpPenalty(null);
			team.setpFreekickShort(null);
			team.setpFreekickLong(null);
			team.setpCornerRight(null);
			team.setpCornerLeft(null);
			team.setpTargetMan(null);

			int captain = 0;
			int throwinright = 0; 
			int throwinleft = 0; 
			int penaltytaker = 0; 
			int freekickshort = 0; 
			int freekicklong = 0; 
			int cornerright = 0; 
			int cornerleft = 0;
			int targetMan = 0;

			if (res.next()){
				captain = res.getInt("captain");
				throwinright = res.getInt("throwinright");
				throwinleft = res.getInt("throwinleft");
				penaltytaker = res.getInt("penaltytaker");
				freekickshort = res.getInt("freekickshort");
				freekicklong = res.getInt("freekicklong");
				cornerright = res.getInt("cornerright");
				cornerleft = res.getInt("cornerleft");
				targetMan = res.getInt("targetman");
			}

			for (Player p : team.getPlayers()){
				if (p.getId() == captain) team.setpCaptain(p);
				if (p.getId() == throwinright) team.setpThrowinRight(p);
				if (p.getId() == throwinleft) team.setpThrowinLeft(p);
				if (p.getId() == penaltytaker) team.setpPenalty(p);
				if (p.getId() == freekickshort) team.setpFreekickShort(p);
				if (p.getId() == freekicklong) team.setpFreekickLong(p);
				if (p.getId() == cornerright) team.setpCornerRight(p);
				if (p.getId() == cornerleft) team.setpCornerLeft(p);
				if (p.getId() == targetMan) team.setpTargetMan(p);
			}
			for (Player p : team.getSubs()){
				if (p.getId() == captain) team.setpCaptain(p);
				if (p.getId() == throwinright) team.setpThrowinRight(p);
				if (p.getId() == throwinleft) team.setpThrowinLeft(p);
				if (p.getId() == penaltytaker) team.setpPenalty(p);
				if (p.getId() == freekickshort) team.setpFreekickShort(p);
				if (p.getId() == freekicklong) team.setpFreekickLong(p);
				if (p.getId() == cornerright) team.setpCornerRight(p);
				if (p.getId() == cornerleft) team.setpCornerLeft(p);
				if (p.getId() == targetMan) team.setpTargetMan(p);
			}

			res.close();
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		System.out.println("Team roles updated. Throw in right: " + team.getpThrowinRight().getLastname());
	}

	/**
	 * Set the lineup of a team for a given match
	 * @param team
	 * @param matchId
	 */
	public static void setLineup(Team team, int matchId){
		String sql;
		sql = "INSERT INTO match_lineups (match_id, club_id, pl1id, pl2id, pl3id, pl4id, pl5id, pl6id, pl7id, pl8id, pl9id, pl10id, pl11id, pl12id, pl13id, pl14id, pl15id, pl16id, ";
		sql += "pl17id, pl18id, subtime1, subtime2, subtime3, suboff1, suboff2, suboff3, subon1, subon2, subon3) values (" + matchId + ", " + team.getId() + ", ";
		System.out.println("Team size" + team.getPlayers().size());
		for (Player p : team.getPlayers())
			sql += p.getId() + ", ";
		if (team.getPlayers().size() < 11)
			for (int i = 0; i < 11 - team.getPlayers().size(); i++)
				sql += "null, ";
		for (Player p : team.getSubs())
			sql += p.getId() + ", ";
		for (int i = 0; i < 7 - team.getSubs().size(); i++)
			sql += "null, ";
		
		sql += "null, null, null, null, null, null, null, null, null);";
		System.out.println(sql);
		try {
			executeSimpleStatement(sql);
		} catch (SQLException e) {
			System.out.println("Couldn't set team sheet: " + e.getMessage());
		}
	}
	
	/**
	 * adds a player and his club to match_playerstats for a given match
	 * @param matchId
	 * @param personId
	 * @param clubId
	 */
	public static void addPlayerToPlayerMatchStats(int matchId, int personId, int clubId, int leagueFame){
		try {
			executeSimpleStatement("INSERT INTO match_playerstats (match_id, person_id, club_id, league_fame) VALUES " +
					"(" + matchId + ", " + personId + ", " + clubId + ", " + leagueFame + ");");
		} catch (SQLException e) {
			System.out.println("Exception: " + e.getMessage());
		}
	}
	
	public static ArrayList<String> getManagerCommands(int matchId, String matchtime){
		ArrayList<String> result = new ArrayList<String>();
		Statement stmt;

		String sql = "SELECT * FROM manager_commands WHERE match_id=" + matchId + " AND status = 0";

		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			ResultSet res = stmt.executeQuery(sql);

			while (res.next()){
				result.add(res.getString("command"));
				System.out.println();
				System.out.println("managercommand: " + (new Date()).toString() + " - " + res.getString("command"));
			}
			res.close();

			stmt.execute("UPDATE manager_commands SET status = 1, statusmsg = 'Collected by match engine', matchtimeused = '" + matchtime + "' WHERE match_id=" + matchId + " AND status = 0");

			tacticsChangeNumber--;

			stmt.close();

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return result;
	}

	public static void setMatchStatus(int matchId, int status){

		Statement stmt;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			String sql = "UPDATE matches SET status=" + status + " WHERE id=" + matchId + ";";
			stmt.execute(sql);
			stmt.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static int[] getTeams(int matchId){
		int result[] = new int[2];

		Statement stmt;
		try {
			openConnection();
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			String sql = "SELECT homeTeamId, awayTeamId FROM matches WHERE id=" + matchId + ";";
			ResultSet res = stmt.executeQuery(sql);
			if (res.next()){
				result[0] = res.getInt(1);
				result[1] = res.getInt(2);
			}
			res.close();
			stmt.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return result;
	}

	public static String getNextMatchTime(int id) throws SQLException{

		Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

		String sql = "SELECT matchDate FROM matches WHERE id=" + id;
		ResultSet res = stmt.executeQuery(sql);

		String result = "";

		if (res.next())
			result = res.getString("matchDate");

		res.close();
		stmt.close();

		return result;
	}

	public static void updateMatchStat(int matchId, int clubId, String stat, int value){

		Statement stmt;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			String sql = "UPDATE matchstats SET " + stat + "=" + value + " WHERE ";
			sql += "match_id=" + matchId + " AND club_id=" + clubId + ";";
			stmt.execute(sql);
			//			System.out.println(sql);
			stmt.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void addToMatchStat(int matchId, int clubId, String stat){

		Statement stmt;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			String sql = "UPDATE matchstats SET " + stat + "=" + stat + "+1 WHERE ";
			sql += "match_id=" + matchId + " AND club_id=" + clubId + ";";
			stmt.execute(sql);
			//			System.out.println(sql);
			stmt.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void createMatchStats(int matchId, int clubId){

		Statement stmt;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			String sql = "INSERT INTO matchstats (match_id, club_id, shots,";
			sql += " ontarget, possession, corners, freekicks,";
			sql += " throwins, fouls, offsides, yellowcards,";
			sql += " redcards) VALUES (" + matchId + ", " + clubId + ", 0,";
			sql += " 0, 0, 0, 0, 0, 0, 0, 0, 0);";
			stmt.execute(sql);

			stmt.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


	}

	public static void writeEnergyToDB(ArrayList<Player> players){
		Statement stmt;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			String sql = "";

			for (Player p : players){
				sql += "UPDATE persons SET energy=" + (int)p.getEnergy() + " WHERE id=" + p.getId() + "; ";
			}

			stmt.execute(sql);
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	//Poorly tested
	/*
	 * Returns an int representing the average of the 10 highest transfer fees paid
	 */
	public static void UpdateOrCreatePlayerRating(Player player, int rating, int value, int bestPosition, int avgRating, double avgMatchRating, double avgMatchRatingNorm){
		
		try {
			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			String sql = "UPDATE player_ratings SET best_rating = " + rating + 
												", value = " + value + 
												", best_position = " + bestPosition + 
												", avg_rating = " + avgRating +
												", gk_rating = " + (int)player.getGoalkeeperScore() +
												", cb_rating = " + (int)player.getDefenderScore() + 
												", fb_rating = " + (int)player.getFullbackScore() + 
												", cm_rating = " + (int)player.getMidfielderScore() + 
												", wg_rating = " + (int)player.getWingerScore() + 
												", st_rating = " + (int)player.getStrikerScore() +
												", avg_match_rating = " + avgMatchRating + 
												", avg_match_rating_normalized = " + avgMatchRatingNorm +
												" WHERE person_id = " + player.getId();
			stmt.execute(sql, Statement.RETURN_GENERATED_KEYS);
			ResultSet res = stmt.getGeneratedKeys();
			
			if (res.next() && res.getInt(1) > 0){
				//A row was found and updated
			}
			else{
				//No row found for this player - insert one
				sql = "INSERT INTO player_ratings (person_id, best_rating, value, best_position, avg_rating," +
											"gk_rating, cb_rating, fb_rating, cm_rating, wg_rating, st_rating, " +
											"avg_match_rating, avg_match_rating_normalized) VALUES (" + 
						player.getId() + ", " + rating + ", " + value + ", " + bestPosition + 
						", " + avgRating +
						", " + (int)player.getGoalkeeperScore() + 
						", " + (int)player.getDefenderScore() + 
						", " + (int)player.getFullbackScore() + 
						", " + (int)player.getMidfielderScore() + 
						", " + (int)player.getWingerScore() + 
						", " + (int)player.getStrikerScore() +
						", " + avgMatchRating +
						", " + avgMatchRatingNorm + 
						")";
				stmt.execute(sql);
			}
			
			res.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}

	
	}
	
	//Poorly tested
	/*
	 * Returns an int representing the average of the 10 highest transfer fees paid
	 */
	public static int getAvgTopTransfers(){
		int result = 0;
		
		try {
			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			
			String sql = "SELECT avg(transferfee)::int as result FROM (SELECT transferfee FROM contracts WHERE transferfee > 0 and acceptedbyplayer=true ORDER BY transferfee DESC LIMIT 10) a";
			ResultSet res = stmt.executeQuery(sql);

			if (res.next()){
				result = res.getInt("result");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	public static Match loadMatch(int matchId, MatchEngine engine) {
		Match result = new Match(engine);

		result.setMatchId(matchId);
		result.setState(MatchState.KICK_OFF);

		try {
			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			String sql = "SELECT * FROM matches m INNER JOIN leagues l ON m.league_id=l.id LEFT OUTER JOIN competition_stages s ON l.current_stage=s.number and s.league_id=l.id WHERE m.id=" + matchId;
			ResultSet res = stmt.executeQuery(sql);

			if (res.next()){
				result.setLeagueId(res.getInt("league_id"));
				result.setMatchrep(res.getInt("leaguereputation"));
				result.setSeasonId(res.getInt("season_id"));
				result.setTwo_legs(res.getBoolean("two_legs"));
				result.setCup(res.getBoolean("cup"));
			}
			else{
				System.out.println("Match info missing");
			}

			res.close();
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	/**
	 * Deletes failed negotiations older than daysOld days
	 */
	public static void removeOldFailedNegotiations(int daysOld){		

		try {
			String sql = "DELETE FROM failed_contract_negotiations WHERE date < now() - interval '" + daysOld + " days'";
			stmt.execute(sql);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
		
		System.out.println("Old failed negotiations deleted");
	}
	
	/**
	 * Gets all ids of active players 
	 * @return an ArrayList<Integer> containing personID
	 */
	public static ArrayList<Integer> getAllPlayerIDs(){		
		
		ArrayList<Integer> result = new ArrayList<Integer>();
		
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			
			String sql = "SELECT id FROM persons WHERE retired = false;";
			ResultSet res = stmt.executeQuery(sql);
			
			while (res.next()){
				result.add(res.getInt("id"));
			}
			res.close();
			
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
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
	public static Player loadPlayer(int id)throws SQLException, ClassNotFoundException{		
		stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);		
		String sql = "SELECT * FROM persons WHERE id = " + id + ";";
		ResultSet res = stmt.executeQuery(sql);
		Player p = null;
		
		if (res.next()){
			p = new Player();
			p.setId(id);
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
		}
		if (res != null) res.close();	
		return p;
	}
	

	/**
	 * Gets the owner id (user_id) of the club a player is contracted to.
	 */
	public static int getPlayerClubOwner(int personId){
		
		int result = -1;
		String sql = "SELECT user_id FROM clubs WHERE id = (SELECT club_id FROM contracts WHERE enddate > now() AND acceptedbyplayer=true AND person_id=" + personId + ")";		

		try {
			ResultSet res = stmt.executeQuery(sql);
		
			if (res.next()){
				result = res.getInt("user_id");
			}
			
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return result;
	}
	
	/**
	 * Gets the club id of the club a player is contracted to. If the player hasn't got an active contract -1 is returned
	 */
	public static int getPlayerClubId(int personId){
		
		int result = -1;
		String sql = "SELECT club_id FROM contracts WHERE enddate > now() AND acceptedbyplayer=true AND person_id=" + personId;		

		try {
			ResultSet res = stmt.executeQuery(sql);
		
			if (res.next()){
				result = res.getInt("club_id");
			}
			
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return result;
	}
	
	/**
	 * Loads the playerTactics of each player in the given ArrayList of players, 
	 * sets the tactis for each player and returns those players
	 * @param players The players we want to get tactics from
	 * @return
	 * @throws SQLException
	 * @throws ClassNotFoundException
	 */
	public static ArrayList<Player> loadPlayerTactics(ArrayList<Player> players) throws SQLException, ClassNotFoundException{
		String sql;
		Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
		ResultSet tactics;
		ArrayList<Player> playersDone = new ArrayList<Player>();
			
		for(Player p : players){
			sql = "SELECT * FROM playertactics WHERE person_id=" + p.getId();
			tactics = stmt.executeQuery(sql);
			if (tactics.next()){
				p.setAggression(tactics.getByte("aggression"));
				p.setClosingdown(tactics.getByte("closingdown"));
				p.setMentality(tactics.getByte("mentality"));
				p.setLongshots(tactics.getByte("longshots"));
				p.setThroughballs(tactics.getByte("throughballs"));
				p.setDribble(tactics.getByte("dribble"));
				p.setRuns(tactics.getByte("runs"));
				p.setCrossball(tactics.getByte("crossball"));
				p.setPressing(tactics.getByte("tightmarking"));
				p.setShortLongPassing(tactics.getByte("passing")); 
				p.setForwardOnSetpieces(tactics.getBoolean("forwardOnSetPieces"));
			}
			else{
				p.setAggression((byte)50);
				p.setClosingdown((byte)50);
				p.setMentality((byte)50);
				p.setLongshots((byte)50);
				p.setThroughballs((byte)50);
				p.setDribble((byte)50);
				p.setRuns((byte)50);
				p.setCrossball((byte)50);
				p.setPressing((byte)50);
				p.setShortLongPassing((byte)50); 
				p.setForwardOnSetpieces(false);
			}
			
			playersDone.add(p);
			if (tactics != null) tactics.close();
		}		
		return playersDone;
	}
	
	/**
	 * Gets the lineup of a given team. 
	 * @param team the team from which you want to get the lineup
	 * @return int[] A list of id's for the person_ids of the players in the lineup. 
	 * @throws SQLException
	 * @throws ClassNotFoundException
	 */
	public static int[] loadLineup(Team team) throws SQLException, ClassNotFoundException{
		Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
		String sql = "SELECT * FROM lineups WHERE club_id=" + team.getId();		
		ResultSet res = stmt.executeQuery(sql);
		
		int[] lineup = new int[16];		
		if (res.next()){
			for (int i = 0; i < 16; i++){				
				lineup[i] = res.getInt(i+3);				
			}
		}	
		if (res != null) res.close();
		return lineup;
	}
	
	/**
	 * Gets the x,y positions on a given team.
	 * @param team 
	 * @return String[] A list of String positions "160.3, 145"
	 * @throws SQLException
	 * @throws ClassNotFoundException
	 */
	public static String[] loadPositions(Team team) throws SQLException, ClassNotFoundException{
		Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
		String sql = "SELECT * FROM lineups WHERE club_id=" + team.getId();
		ResultSet res = stmt.executeQuery(sql);	
		String[] positions = new String[11];
		if (res.next()){
			for (int i = 0; i < 16; i++){
				if (i < 11) {
					positions[i] = res.getString("pos" + (i+1));
				}
			}			
		}
		if (res != null) res.close();
		return positions;
	}
	
	/**
	 * Gets the player tactics for a given team. (captain, throwinright, throwinleft, penaltytaker, freekickshort, 
	 * freekicklong, cornerright, cornerleft, targetman)
	 * @param team
	 * @return int[] A list of ids (person_id/player.id) representing which person has which tactic
	 * int[0] = captain, int[1] = throwinright, int[2] = throwinleft, int[3] = penaltytaker, int[4] = freekickshort
	 * int[5] = freekicklong, int[6] = cornerright, int[7] = cornerleft, int[8] = targetman
	 * 
	 * @throws SQLException
	 * @throws ClassNotFoundException
	 */
	public static int[] loadPlayerTeamTactics(Team team) throws SQLException, ClassNotFoundException{
		Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
		String sql = "SELECT * FROM teamtactics WHERE club_id=" + team.getId();
		ResultSet res = stmt.executeQuery(sql);	
		
		int[] teamTactics = new int[9];
		if (res.next()){
			teamTactics[0] = res.getInt("captain");
			teamTactics[1] = res.getInt("throwinright");
			teamTactics[2] = res.getInt("throwinleft");
			teamTactics[3] = res.getInt("penaltytaker");
			teamTactics[4] = res.getInt("freekickshort");
			teamTactics[5] = res.getInt("freekicklong");
			teamTactics[6] = res.getInt("cornerright");
			teamTactics[7] = res.getInt("cornerleft");
			teamTactics[8] = res.getInt("targetman");
		}
		if (res != null) res.close();
		return teamTactics;
	}
	
//	public static ArrayList<Player> loadPlayers(Team team) throws SQLException, ClassNotFoundException{
//		System.out.println("TEAM NAME: " + team.getName());
//		Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
//
//		String strLineup = "";
//		String sql = "SELECT * FROM lineups WHERE club_id=" + team.getId();
//		ResultSet res = stmt.executeQuery(sql);
//		
//		int[] lineup = new int[16];
//		
//		//		int[] roles = new int[11];
//		String[] positions = new String[11];
//
//		if (res.next()){
//			for (int i = 0; i < 16; i++){
//				strLineup += res.getInt(i+3) + ",";
//				lineup[i] = res.getInt(i+3);
//				if (i < 11) {
//					//					roles[i] = res.getInt("role" + (i+1));
//					positions[i] = res.getString("pos" + (i+1));
//					System.out.println("pos = " + positions[i]);
//				}
//			}
//			
//		}
//		System.out.println("strLineup " + strLineup);
//		while (strLineup.endsWith(",") || strLineup.endsWith("-"))
//			strLineup = strLineup.substring(0, strLineup.length() - 2);
//
//		//QUESTION MARK
//		try{
//			String q = "INSERT INTO match_playertactics (SELECT * FROM playertactics WHERE person_id IN (" + strLineup + "));";
//			//			System.out.println(q);
//			executeSimpleStatement(q);
//		}catch (Exception e) {
//			e.printStackTrace();
//		}
//		//END QUESTION MARK
//
//		Team.validateLineup(positions);
//
//		sql = "SELECT * FROM teamtactics WHERE club_id=" + team.getId();
//		res = stmt.executeQuery(sql);
//
//		int captain = 0;
//		int throwinright = 0; 
//		int throwinleft = 0; 
//		int penaltytaker = 0; 
//		int freekickshort = 0; 
//		int freekicklong = 0; 
//		int cornerright = 0; 
//		int cornerleft = 0;
//		int targetMan = 0;
//
//		if (res.next()){
//			captain = res.getInt("captain");
//			throwinright = res.getInt("throwinright");
//			throwinleft = res.getInt("throwinleft");
//			penaltytaker = res.getInt("penaltytaker");
//			freekickshort = res.getInt("freekickshort");
//			freekicklong = res.getInt("freekicklong");
//			cornerright = res.getInt("cornerright");
//			cornerleft = res.getInt("cornerleft");
//			targetMan = res.getInt("targetman");
//		}
//
//		boolean keeperSet = false;
//
//		ArrayList<Player> players = new ArrayList<Player>();
//
//		for (int i = 0; i < 16; i++){
//			sql = "SELECT * FROM persons p WHERE (SELECT count(*) FROM contracts WHERE person_id=p.id AND club_id=" + team.getId() + " AND enddate > now() AND acceptedbyplayer='t') > 0 AND id=" + lineup[i];
//			res = stmt.executeQuery(sql);
//
//			if (res.next()){
//
//				Player p = new Player();
//				p.setId(res.getInt("id"));
//				p.setAge(res.getInt("age"));
//				p.setFirstname(res.getString("firstName"));
//				p.setLastname(res.getString("lastName"));
//				p.setAcceleration(res.getDouble("acceleration"));
//				p.setTopSpeed((res.getDouble("topSpeed")*0.75) + 96 + 40);				
//				p.setDribbling(res.getDouble("dribbling"));
//				p.setStrength(res.getDouble("strength"));
//				p.setTackling(res.getDouble("tackling"));
//				p.setAgility(res.getDouble("agility"));
//				p.setReaction(res.getDouble("reaction"));
//				p.setHandling(res.getDouble("handling"));
//				p.setShooting(res.getDouble("shooting"));
//				p.setShotpower(res.getDouble("shotPower"));
//				p.setPassing(res.getDouble("passing"));
//				p.setTechnique(res.getDouble("technique"));
//				p.setHeight(res.getDouble("height"));
//				p.setVision(res.getDouble("vision"));
//				p.setJumping(res.getDouble("jumping"));
//				p.setStamina(res.getDouble("stamina"));
//				p.setShirtNumber(res.getInt("shirtnumber"));
//				p.setHeading(res.getDouble("heading")); 
//				p.setMarking(res.getDouble("marking"));
//				p.setEnergy(res.getDouble("energy"));
//				p.setMorale(res.getDouble("morale"));
//				p.setCommandOfArea(res.getDouble("commandofarea"));
//				p.setShotstopping(res.getDouble("shotstopping"));
//				p.setRushingout(res.getDouble("rushingout"));
//
////				p.totalPPFromFacc = res.getDouble("totalPPFromFacc");
////				p.totalPPFromXP = res.getDouble("totalPPFromXP");
//
//				//taktik
//				sql = "SELECT * FROM playertactics WHERE person_id=" + res.getInt("id");
//				ResultSet tactics = stmt.executeQuery(sql);
//
//				p.setStart_acceleration(p.getAcceleration());
//				p.setStart_agility(p.getAgility());
//				p.setStart_dribbling(p.getDribbling());
//				p.setStart_Energy(p.getEnergy());
//				p.setStart_morale(p.getMorale());
//				p.setStart_handling(p.getHandling());
//				p.setStart_heading(p.getHeading());
//				p.setStart_passing(p.getPassing());
//				p.setStart_jumping(p.getJumping());
//				p.setStart_marking(p.getMarking());
//				p.setStart_reaction(p.getReaction());
//				p.setStart_shooting(p.getShooting());
//				p.setStart_shotpower(p.getShotpower());
//				p.setStart_strength(p.getStrength());
//				p.setStart_tackling(p.getTackling());
//				p.setStart_technique(p.getTechnique());
//				p.setStart_topSpeed(p.getTopSpeed());
//				p.setStart_vision(p.getVision());
//				p.setStart_commandofarea(p.getCommandOfArea());
//				p.setStart_shotstopping(p.getShotstopping());
//				p.setStart_rushingout(p.getRushingout());
//
//
//				if (tactics.next()){
//					p.setAggression(tactics.getByte("aggression"));
//					p.setClosingdown(tactics.getByte("closingdown"));
//					p.setMentality(tactics.getByte("mentality"));
//					p.setLongshots(tactics.getByte("longshots"));
//					p.setThroughballs(tactics.getByte("throughballs"));
//					p.setDribble(tactics.getByte("dribble"));
//					p.setRuns(tactics.getByte("runs"));
//					p.setCrossball(tactics.getByte("crossball"));
//					p.setPressing(tactics.getByte("tightmarking"));
//					p.setShortLongPassing(tactics.getByte("passing")); 
//					p.setForwardOnSetpieces(tactics.getBoolean("forwardOnSetPieces"));
//				}
//				
//				if (i < 11){
//					String s = positions[i];
//					String pos[] = s.split(",");
//
//					p.setStartPosX(Integer.parseInt(pos[0]));
//					p.setStartPosY(Integer.parseInt(pos[1]));
//
//					if (!keeperSet){
//						p.setRole(PlayerRole.GK);
//						keeperSet = true;
//					}
//					else
//						p.setRole(PlayerRole.CM);
//				}
//				else{
//					//					System.out.println(p.getShirtNumber());
//					p.setStartPosX(250 + i * 10);
//					p.setStartPosY(-20);
//					p.setRole(PlayerRole.SUB);
//				}
//
//				if (p.getId() == captain) team.setpCaptain(p);
//				if (p.getId() == throwinright) team.setpThrowinRight(p);
//				if (p.getId() == throwinleft) team.setpThrowinLeft(p);
//				if (p.getId() == penaltytaker) team.setpPenalty(p);
//				if (p.getId() == freekickshort) team.setpFreekickShort(p);
//				if (p.getId() == freekicklong) team.setpFreekickLong(p);
//				if (p.getId() == cornerright) team.setpCornerRight(p);
//				if (p.getId() == cornerleft) team.setpCornerLeft(p);
//				if (p.getId() == targetMan) team.setpTargetMan(p);
//
//				p.setMyTeam(team);
//				players.add(p);
//
//
//				if (tactics != null) tactics.close();
//
//			}
//			else{
//				if (lineup[i] != -1)
//					System.out.println(lineup[i] + " Spiller ikke i klubben");
//			}
//		}
//
//		if (res != null) res.close();
//		if (stmt != null) stmt.close();
//
//		return players;
//	}

	public static double wageDifferenceToMorale(int wage, int expectedWage){
		double result = 0;

		result = Math.sqrt(Math.abs(wage - expectedWage) / (expectedWage / 100.0)) / 1.5;

		if (wage < expectedWage)
			result *= -1;

		return result;
	}


	/**
	 * Update morale based on match result
	 */
	public static void updateMatchMorale(int home, int away, Team homeTeam, Team awayTeam){

		//Morale can be update based on fame. The diffrence in fame influences how significant the result was for each team. 
		//A top club beating a club from the bottom of the table will not get as much fame as the bottom 
		int id = 0;
		PlayerMorale pMorale = new PlayerMorale();

		try{

			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			Statement stmtP = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			ResultSet res = stmtP.executeQuery("SELECT p.id, morale, club_id FROM persons p INNER JOIN contracts c ON p.id=c.person_id WHERE enddate > now() AND acceptedbyplayer = true AND (c.club_id = " + homeTeam.getId() + " OR c.club_id = " + awayTeam.getId() + ") ORDER BY club_id");

			System.out.println("homefame: " + homeTeam.getFame());
			System.out.println("awayfame: " + awayTeam.getFame());

			double fameRate = (double)homeTeam.getFame() / (double)awayTeam.getFame();

			System.out.println("fameRate: " + fameRate);

			double moraleChangeHome = 0;
			double moraleChangeAway = 0;

			if (home < away){
				moraleChangeHome = (2.5 * fameRate + 1) * -1;
				moraleChangeAway = (2.5 * fameRate + 1);
			}
			else if(home == away){
				moraleChangeHome = (1 - fameRate) * 2.5;
				moraleChangeAway = (fameRate - 1) * 2.5;
			}
			else{
				moraleChangeHome = (2.5 / fameRate + 1);
				moraleChangeAway = (2.5 / fameRate + 1) * -1;
			}

			System.out.println("moraleChangeHome: " + moraleChangeHome);
			System.out.println("moraleChangeAway: " + moraleChangeAway);

			while (res.next()){
				id = res.getInt("id");
				double morale = res.getDouble("morale");
				double change = 0;
				String opp = "";

				if (res.getInt("club_id") == homeTeam.getId()){
					morale = PlayerMorale.calcNewMorale(res.getDouble("morale"), moraleChangeHome);
					change = moraleChangeHome;
					opp = awayTeam.getName();
				}
				else if (res.getInt("club_id") == awayTeam.getId()){
					morale = PlayerMorale.calcNewMorale(res.getDouble("morale"), moraleChangeAway);
					change = moraleChangeAway;
					opp = homeTeam.getName();
				}

				String sql = "UPDATE persons SET morale = " + morale + " WHERE id = " + id;
				//				System.out.println(sql);
				stmt.execute(sql);
				if (change > 5){
					sql = "INSERT INTO person_thoughts (person_id, type, negative, msg) VALUES (" + id + ", 3, false, 'Delighted with the result against " + opp + "')";
					stmt.execute(sql);
				}
				else if (change > 2){
					sql = "INSERT INTO person_thoughts (person_id, type, negative, msg) VALUES (" + id + ", 3, false, 'Happy with the result against " + opp + "')";
					stmt.execute(sql);
				}
				else if (change < -5){
					sql = "INSERT INTO person_thoughts (person_id, type, negative, msg) VALUES (" + id + ", 3, true, 'Gutted with the result against " + opp + "')";
					stmt.execute(sql);
				}
				else if (change < -2){
					sql = "INSERT INTO person_thoughts (person_id, type, negative, msg) VALUES (" + id + ", 3, true, 'Disappointed with the result against " + opp + "')";
					stmt.execute(sql);
				}
			}

			con.commit();
			con.setAutoCommit(true);

			res.close();
			stmtP.close();
			stmt.close();

		} catch (SQLException e) {
			try {
				con.rollback();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
			System.out.println(e.getStackTrace());
			System.out.println(e.getMessage());
			System.out.println("Last id: " + id);
		}
	}
	
	/**
	 * Method that checks offers from agents to players and responds to them. 
	 */
	public static void playersReactToAgentOffers(){
		//Find id's of all players who have offers
		String sql = "SELECT person_id, lastname, age FROM agent_contracts c INNER JOIN persons p on p.id=c.person_id WHERE accepted IS NULL;";
		Random r = new Random();

		try {
			Statement stmt2 = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			ResultSet res = stmt.executeQuery(sql);

			//For each player with offers
			while (res.next()){

				//Check all offers and pick the best one a few days after the first one
				sql = "SELECT c.id as cid, (EXTRACT(epoch FROM now() - offered)/3600)::int as hours_old, * FROM agent_contracts c INNER JOIN users u ON c.user_id=u.id WHERE accepted IS NULL AND person_id = " + res.getInt("person_id");
				ResultSet res2 = stmt2.executeQuery(sql);

				ArrayList<Integer> allUserIDs = new ArrayList<Integer>();
				int bestOfferId = -1;
				int bestOfferUserId = -1;
				int bestOfferScore = -1;
				int bestOfferWagePercent = -1;
				int maxHoursOld = -1;

				while (res2.next()){
					allUserIDs.add(res2.getInt("user_id"));
					if (res2.getInt("hours_old") > maxHoursOld) maxHoursOld = res2.getInt("hours_old");

					int offerScore = res2.getInt("agent_reputation");
					System.out.println("id: " + res2.getInt("cid"));
					System.out.println("pwage: " + res2.getInt("percent_wage"));
					System.out.println("pfee: " + res2.getInt("percent_fee"));
					if (res2.getInt("percent_wage") > 0) offerScore /= res2.getInt("percent_wage") / 3.0;
					if (res2.getInt("percent_fee") > 0) offerScore /= res2.getInt("percent_fee") / 6.0;

					if (offerScore > bestOfferScore){
						bestOfferScore = offerScore;
						bestOfferId = res2.getInt("cid");
						bestOfferUserId = res2.getInt("user_id");
						bestOfferWagePercent = res2.getInt("percent_wage");
					}
				}

				res2.close();

				//Consider offers when the first one was sent between 24-72 hours ago
				if (maxHoursOld > 24 + r.nextInt(48)){
					//Run everything as a transaction 
					con.setAutoCommit(false);

					//Send a message to all agents who offered representation
					for (int i : allUserIDs){
						//Players will only accept decent offers if they are at least 16 years old
						//Players 14-15 years old will accept any offer if it's the best they have
						//This allows agents with 0 reputation to get back in the game - but only if they take less than 10% of wages
						if (bestOfferScore < 40 && res.getInt("age") / 365 > 15){
							sendMessage(i, 99, res.getString("lastname") + " rejects offer", res.getString("lastname") + " has rejected your offer of representation stating that the offer simply wasn''t attractive enough.");
						}
						else if (bestOfferScore < 40 && bestOfferWagePercent > 10){
							sendMessage(i, 99, res.getString("lastname") + " rejects offer", res.getString("lastname") + " has rejected your offer of representation stating that the offer simply wasn''t attractive enough.");
						}
						else{
							
							if (i == bestOfferUserId){
								sendMessage(i, 99, res.getString("lastname") + " accepts offer", res.getString("lastname") + " has accepted your offer of representation and you are now the players official agent.");
								stmt2.execute("UPDATE agent_contracts SET accepted=now() WHERE id=" + bestOfferId);
								stmt2.execute("UPDATE persons SET user_id = " + bestOfferUserId + " WHERE id=" + res.getInt("person_id"));
							}
							else{
								sendMessage(i, 99, res.getString("lastname") + " rejects offer", res.getString("lastname") + " has rejected your offer of representation as he has now appointed another agent.");
							}
						}
					}
					
					stmt2.execute("DELETE FROM agent_contracts WHERE accepted IS NULL AND person_id=" + res.getInt("person_id"));

					//All the queries are ready. Commit
					con.commit();
					con.setAutoCommit(true);
				}

			}
			res.close();

		} catch (Exception e) {
			e.printStackTrace();
			//An error occurred. Roll back 
			try {
				con.rollback();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
		}
		System.out.println("Players reacted to agent offers.");
	}

	/**
	 * Returns a resultset containing players and their opinions of their agents
	 */
	public static ArrayList<Player> getAgentOpinions(){
		ResultSet res = null;
		String sql = "SELECT p.id, agent_loyalty, opinion, p.user_id, firstname, lastname FROM persons p INNER JOIN agent_opinions o ON p.id=o.person_id AND p.user_id=o.user_id WHERE p.user_id != 99;";
		ArrayList<Player> result = new ArrayList<Player>();
		
		try {
			res = stmt.executeQuery(sql);
			while (res.next()){
				Player p = new Player();
				p.setFirstname(res.getString("firstname"));
				p.setLastname(res.getString("lastname"));
				p.userId = res.getInt("user_id");
				p.setId(res.getInt("id"));
				p.agentLoyalty = res.getInt("agent_loyalty");
				p.agentOpinion = res.getDouble("opinion");
				
				result.add(p);
			}

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	/**
	 * Returns a player containing totalPPFromFacc and totalPPFromXP
	 */
	public static Player getPlayerTotalXPAndTraining(int playerId){
		Player result = new Player();
		String sql = "SELECT totalppfromfacc, totalppfromxp FROM persons WHERE id=" + playerId;

		try {
			ResultSet res = stmt.executeQuery(sql);

			if (res.next()){
				result.totalPPFromFacc = res.getInt("totalppfromfacc");
				result.totalPPFromXP = res.getInt("totalppfromxp");
			}
			res.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}
			
			
	/**
	 * Returns a string containing a random (based on popularity) firstname from a given country
	 */
	public static String getRandomName(boolean firstname, int countryId){

		String result= "";
		int type = 1;
		if (!firstname) type = 3;
		String sql = "SELECT * FROM names WHERE country_id=" + countryId + " AND type=" + type + " ORDER BY random()*(1/popularity::real) LIMIT 1;";

		try {
			ResultSet res = stmt.executeQuery(sql);

			if (res.next()){
				result = res.getString("name");
			}
			res.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}


	/**
	 * Saves a new scout report 
	 */
	public static void saveScoutReport(int scoutAssignmentId, int personId, String report, int potential, int detailLevel){

		String sql = "INSERT INTO scout_reports (scout_assignment_id, ";
		sql += "person_id, report, potential, detail_level) VALUES (";
		sql += scoutAssignmentId + ", " + personId + ", '" + report + "', " + potential + ", " + detailLevel + ")";

		try {
			stmt.execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Update scouting assignments to start now if they're queued for users who don't have any active assignments
	 */
	public static void startNewScoutAssignments(){

		String sql = "UPDATE scout_assignments SET start=now() WHERE id in " +
				"(WITH assignments AS (  " +
				"SELECT s.id, " +
				"s.user_id, " +
				"ROW_NUMBER() OVER(PARTITION BY s.user_id ORDER BY s.assigned) AS rk  " +   
				"FROM scout_assignments s WHERE finished IS NULL)   " +  
				"SELECT a.id  " +   
				"FROM assignments a   " +  
				"WHERE a.rk = 1  " + 
				"AND (SELECT count(*) FROM scout_assignments WHERE user_id=a.user_id AND NOT start IS NULL AND finished IS NULL) = 0)";

		try {
			stmt.execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns an arraylist of current scouting assignments
	 */
	public static ArrayList<ScoutAssignment> getCurrentScoutingAssignments(){

		ArrayList<ScoutAssignment> result = new ArrayList<ScoutAssignment>();

		String sql = "SELECT id, user_id, country_id, person_id, COALESCE(detail_level, 1) as detail_level, (extract('epoch' FROM now()-start) / 3600)::int as hoursold " +
				"FROM scout_assignments WHERE NOT start IS NULL AND finished IS NULL;";

		try {
			ResultSet res = stmt.executeQuery(sql);

			while (res.next()){
				result.add(new ScoutAssignment(res.getInt("id"), res.getInt("user_id"), 
						res.getInt("person_id"), res.getInt("country_id"), res.getInt("hoursold"), res.getInt("detail_level")));
			}
			res.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}



	/**
	 * Returns a array of doubles containing the players potentials
	 */
	public static double[] getPlayerPotentials(int personId){

		double[] result = new double[22];
		String sql = "SELECT basic_pp FROM player_potentials WHERE person_id=" + personId + " ORDER BY age;";

		try {
			ResultSet res = stmt.executeQuery(sql);

			int i = 0;
			while (res.next() && i < 22){
				result[i] = res.getDouble("basic_pp");
				i++;
			}
			res.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	/*
	 * Creates a user expense charging the user the amount of money
	 */
	public static void createUserExpense(int amount, int type, String description, int userId){

		String sql = "INSERT INTO user_expenses (amount, type, date, description, user_id) VALUES (" + 
				amount + ", " + type + ", now(), '" + description + "', " + userId + ");";

		try {
			stmt.execute(sql);

		} catch (SQLException e) {
			e.printStackTrace();
		}
	}


	/*
	 * Finishes a scouting assignment
	 */
	public static void finishScoutAssignment(int assignmentId){

		String sql = "UPDATE scout_assignments SET finished=now() WHERE id=" + assignmentId;

		try {
			stmt.execute(sql);

		} catch (SQLException e) {
			e.printStackTrace();
		}
	}


	/*
	 * Decreases scouting ability. Used once a day to slowly decrease scouting ability over time. 
	 */
	public static void decreaseScoutingAbility(){

		String sql = "UPDATE users SET scouting=scouting-(scouting / 1500) WHERE scouting > 10";

		try {
			stmt.execute(sql);

		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * Increases scouting ability and country knowledge - i.e. when a scout report has been generated
	 */
	public static void increaseScoutingAbilityAndCountryKnowledge(int userId, int countryId){

		String sql = "UPDATE users SET scouting=scouting+1/(scouting*0.3+1) WHERE id=" + userId + " AND scouting < 100";

		try {
			stmt.execute(sql);

			boolean countryKnowledgeExists = false;

			sql = "SELECT count(*) FROM user_country_knowledges WHERE user_id=" + userId + " AND country_id=" + countryId;
			ResultSet res = stmt.executeQuery(sql);

			if (res.next()){
				if (res.getInt("count") > 0)
					countryKnowledgeExists = true;
			}
			res.close();

			if (countryKnowledgeExists)
				sql = "UPDATE user_country_knowledges k SET knowledge=knowledge+1.0/(SELECT active_players FROM countries WHERE id=k.country_id)*100 WHERE knowledge<100 AND user_id=" + userId + " AND country_id=" + countryId;
			else{
				sql = "INSERT INTO user_country_knowledges (user_id, country_id, knowledge) VALUES (" +
						userId + ", " + countryId + ", 1)";
			}

			stmt.execute(sql);

		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/*
	 * Decreases country knowledge for all users - i.e. when a new player is created
	 */
	public static void decreaseCountryKnowledge(int countryId){

		try {
			String sql = "UPDATE user_country_knowledges k SET knowledge=knowledge-1.0/(SELECT active_players FROM countries WHERE id=k.country_id)*100 WHERE country_id=" + countryId;
			stmt.execute(sql);
			sql = "DELETE FROM user_country_knowledges WHERE knowledge < 0";
			stmt.execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Returns a map of country_id of the country where the person resides and the users knowledge of that country
	 */
	public static Map<Integer, Integer> getCountryKnowledgeFromPersonID(int userId, int personId){

		Map<Integer, Integer> result = new HashMap<Integer, Integer>();
		String sql = "SELECT COALESCE(knowledge, 0) as knowledge, country_id FROM user_country_knowledges WHERE user_id=" + userId +
				" AND country_id=COALESCE((SELECT cl.country_id FROM clubs cl INNER JOIN contracts co ON cl.id=co.club_id " +
				" WHERE person_id=" + personId + " AND enddate>now() AND acceptedbyplayer=true), " +
				"(SELECT country_id FROM persons WHERE id=" + personId + "))" +
				"UNION ALL " +
				"SELECT 0 as knowledge, COALESCE((SELECT cl.country_id FROM clubs cl INNER JOIN contracts co ON cl.id=co.club_id " +  
				"WHERE person_id=" + personId + "  AND enddate>now() AND acceptedbyplayer=true),  " +
				"(SELECT country_id FROM persons WHERE id=" + personId + "))  as country_id";

		try {
			ResultSet res = stmt.executeQuery(sql);

			if (res.next()){
				result.put(res.getInt("country_id"), res.getInt("knowledge"));
			}
			res.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	/**
	 * Returns a the users knowledge of a country
	 */
	public static int getCountryKnowledge(int userId, int countryId){

		int result = 0;
		String sql = "SELECT COALESCE(knowledge, 0) as knowledge FROM user_country_knowledges WHERE " +
				"user_id=" + userId + " AND	country_id=" + countryId;

		try {
			ResultSet res = stmt.executeQuery(sql);

			if (res.next()){
				result = res.getInt("knowledge");
			}
			res.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	/**
	 * Returns the number of active players playing for a club in a country plus players currently without contract originating from the country 
	 */
	public static int getActivePlayersFromCountry(int countryId){

		int result = 0;
		String sql = "SELECT count(*) as result FROM persons p WHERE retired = false AND " +
				"COALESCE((SELECT cl.country_id FROM clubs cl INNER JOIN contracts co ON cl.id=co.club_id " +
				"WHERE person_id=p.id AND enddate>now() AND acceptedbyplayer=true), (SELECT country_id FROM persons WHERE id=p.id))=" + countryId;

		try {
			ResultSet res = stmt.executeQuery(sql);

			if (res.next()){
				result = res.getInt("result");
			}
			res.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	/**
	 * Returns a the users knowledge of a country
	 */
	public static ArrayList<Player> getPersonsForCountryAssignment(int countryId, int assignmentId, boolean includePlayersWithoutPotential){

		ArrayList<Player> result = new ArrayList<Player>();

		String sql = "SELECT * FROM persons p WHERE retired = false AND " +
				"COALESCE((SELECT cl.country_id FROM clubs cl INNER JOIN contracts co ON cl.id=co.club_id " +
				"WHERE person_id=p.id AND enddate>now() AND acceptedbyplayer=true), (SELECT country_id FROM persons WHERE id=p.id))=" + countryId + 
				" AND NOT EXISTS (SELECT person_id FROM scout_reports WHERE scout_assignment_id = " + assignmentId + " AND person_id=p.id)";
		
		if (!includePlayersWithoutPotential)
			sql += " AND id IN (SELECT person_id FROM player_potentials)";

		try {
			ResultSet res = stmt.executeQuery(sql);

			while (res.next()){
				Player p = new Player();
				p.setId(res.getInt("id"));
				p.setAge(res.getInt("age"));
				result.add(p);
			}
			res.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	/**
	 * Returns the users general scouting ability
	 */
	public static int getUserScoutingAbility(int userId){

		int result = 0;
		String sql = "SELECT scouting FROM users WHERE id=" + userId;

		try {
			ResultSet res = stmt.executeQuery(sql);

			if (res.next()){
				result = res.getInt("scouting");
			}
			res.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	/*
	 * Returns an arraylist of players with unanswered contract offers 
	 */
	public static ArrayList<Player> getPlayersWithContractOffers(boolean includePlayersWithAgent){
		ArrayList<Player> result = new ArrayList<Player>();
		String sql = "SELECT * FROM persons WHERE id IN " +
		"(SELECT person_id FROM contracts WHERE offered=true AND acceptedbyclub=true AND acceptedbyplayer=false AND enddate>now())";

		if (!includePlayersWithAgent)
			sql += " AND user_id IS NULL";
			
		try {
			ResultSet res = stmt.executeQuery(sql);

			while (res.next()){
				Player p = new Player();
				
				p.setFirstname(res.getString("firstname"));
				p.setLastname(res.getString("lastname"));
				p.setAge(res.getInt("age"));
				p.agentLoyalty = res.getInt("agent_loyalty");
				p.height = res.getInt("height");
				p.countryId = res.getInt("country_id");

				//Stats
				p.acceleration = res.getDouble("acceleration");
				p.topSpeed = res.getDouble("topSpeed");
				p.reaction = res.getDouble("reaction");
				p.stamina = res.getDouble("stamina");
				p.tackling = res.getDouble("tackling");
				p.marking = res.getDouble("marking");
				p.dribbling = res.getDouble("dribbling");
				p.vision = res.getDouble("vision");
				p.heading = res.getDouble("heading");
				p.shooting = res.getDouble("shooting");
				p.shotpower = res.getDouble("shotpower");
				p.passing = res.getDouble("passing");
				p.technique = res.getDouble("technique");
				p.jumping = res.getDouble("jumping");
				p.handling = res.getDouble("handling");
				p.commandOfArea = res.getDouble("commandOfArea");
				p.shotstopping = res.getDouble("shotstopping");
				p.rushingout = res.getDouble("rushingout");
				p.agility = res.getDouble("agility");
				p.strength = res.getDouble("strength");
				
				result.add(p);
			}
			res.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}
	
	
	/*
	 * Returns a players current contract 
	 */
	public static Contract getPlayerContract(int personId){
		Contract result = null;
		String sql = "SELECT * FROM contracts co INNER JOIN clubs cl ON co.club_id=cl.id WHERE person_id=" + personId + " AND acceptedbyplayer=true AND enddate>now();";

		try {
			ResultSet res = stmt.executeQuery(sql);

			if (res.next()){
				result = new Contract();
				
				result.club_id = res.getInt("club_id");
				result.id = res.getInt("id");
				result.minRelease = res.getInt("minimumreleaseclause");
				result.wage = res.getInt("weeklywage");
				result.signOnFee = res.getInt("signonfee");
				result.transferFee = res.getInt("transferfee");
				result.negotiations = res.getInt("negotiations");
				result.offered = res.getBoolean("offered");
				result.dateOffered = res.getDate("dateoffered");
				result.endDate = res.getDate("enddate");
				result.startDate = res.getDate("startdate");
				result.person_id = res.getInt("person_id");

				Team club = new Team();
				club.setId(res.getInt("club_id"));
				club.setFame(res.getInt("fame"));
				club.ownerId = res.getInt("user_id");
				result.team = club;
			}
			res.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}
	
	
	/*
	 * Returns an arraylist of contract offers for a player 
	 */
	public static ArrayList<Contract> getContractOffersForPlayer(int personId){
		ArrayList<Contract> result = new ArrayList<Contract>();
		String sql = "SELECT * FROM contracts co INNER JOIN clubs cl ON co.club_id=cl.id WHERE person_id=" + personId + " AND acceptedbyclub=true AND acceptedbyplayer=false AND enddate>now();";

		try {
			ResultSet res = stmt.executeQuery(sql);

			while (res.next()){
				Contract c = new Contract();
				
				c.club_id = res.getInt("club_id");
				c.id = res.getInt("id");
				c.minRelease = res.getInt("minimumreleaseclause");
				c.wage = res.getInt("weeklywage");
				c.signOnFee = res.getInt("signonfee");
				c.transferFee = res.getInt("transferfee");
				c.negotiations = res.getInt("negotiations");
				c.offered = res.getBoolean("offered");
				c.dateOffered = res.getDate("dateoffered");
				c.endDate = res.getDate("enddate");
				c.person_id = res.getInt("person_id");

				Team club = new Team();
				club.setId(res.getInt("club_id"));
				club.setFame(res.getInt("fame"));
				club.ownerId = res.getInt("user_id");
				c.team = club;
				
				result.add(c);
			}
			res.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	/**
	 * Returns a player
	 */
	public static Player getPlayer(int personId){

		Player result = new Player();
		String sql = "SELECT * FROM persons WHERE id=" + personId + ";";

		try {
			ResultSet res = stmt.executeQuery(sql);

			if (res.next()){
				result.setFirstname(res.getString("firstname"));
				result.setLastname(res.getString("lastname"));
				result.setAge(res.getInt("age"));
				result.agentLoyalty = res.getInt("agent_loyalty");
				result.height = res.getInt("height");
				result.countryId = res.getInt("country_id");

				//Stats
				result.acceleration = res.getDouble("acceleration");
				result.topSpeed = res.getDouble("topSpeed");
				result.reaction = res.getDouble("reaction");
				result.stamina = res.getDouble("stamina");
				result.tackling = res.getDouble("tackling");
				result.marking = res.getDouble("marking");
				result.dribbling = res.getDouble("dribbling");
				result.vision = res.getDouble("vision");
				result.heading = res.getDouble("heading");
				result.shooting = res.getDouble("shooting");
				result.shotpower = res.getDouble("shotpower");
				result.passing = res.getDouble("passing");
				result.technique = res.getDouble("technique");
				result.jumping = res.getDouble("jumping");
				result.handling = res.getDouble("handling");
				result.commandOfArea = res.getDouble("commandOfArea");
				result.shotstopping = res.getDouble("shotstopping");
				result.rushingout = res.getDouble("rushingout");
				result.agility = res.getDouble("agility");
				result.strength = res.getDouble("strength");
			}
			res.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	/**
	 * Responds to a contract offer by accepting it or deleting it and sends a message to the owner of the club who 
	 * sent the offer
	 */
	public static void respondToContract(boolean accept, int contractId, String message, int recipientId){

		String sql = "";

		try {
			if (accept){
				
				//Pay the agent his fee - not necessary since players who respond here have no agent
				sql = "UPDATE users u SET money = money + COALESCE((SELECT transferfee FROM contracts WHERE id="+contractId+") * " +
						"(SELECT percent_fee FROM agent_contracts WHERE accepted IS NOT NULL AND cancelled IS NULL AND " +
						"user_id=u.id AND person_id=(SELECT person_id FROM contracts WHERE id="+contractId+")) / 100, 0)";
				//stmt.executeQuery(sql);
				
				try{
					//Pay the player his fee
					sql = "UPDATE persons p SET money = money + (SELECT transferfee FROM contracts WHERE id="+contractId+") " +
							"WHERE id = (SELECT person_id FROM contracts WHERE id="+contractId+")";
					stmt.executeQuery(sql);
				}
				catch (Exception e){
					System.out.println("Error paying sign on fee: " + e.getStackTrace());
				}
				
				
				sql = "UPDATE contracts SET acceptedbyplayer=true, startdate=now() WHERE id="+contractId;
				stmt.executeQuery(sql);
				sendMessage(recipientId, 99, "Contract offer accepted", message.replace("'", "''"));
			}
			else{
				sql = "DELETE FROM contracts WHERE id="+contractId;
				stmt.executeQuery(sql);
				sendMessage(recipientId, 99, "Contract offer rejected", message.replace("'", "''"));
			}

		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 *  Returns the percentage of matches started by a player in a club. 
	 *  Only matches where the players current contract was active count.
	 *  
	 *  @param numberOfRecentMatches determines how man matches are included in the calculation
	 */
	public static int percentMatchesStarted(int personId, int clubId, int numberOfRecentMatches){
		int result = 0;
		int possibleMatches = 0;
		int matchesStarted = 0;
		
		try {
			//Find the date the player started playing at the club (player may have left the club and joined again)
			String sql = "SELECT * FROM contracts WHERE acceptedbyplayer=true AND person_id=1224 ORDER BY startdate DESC;";
			
			ResultSet res = stmt.executeQuery(sql);
			String dateStartedAtClub = "now()";
			boolean done = false;
			
			while (res.next() && !done){
				dateStartedAtClub = res.getString("startdate");
				if (res.getInt("club_id") != clubId)
					done = true;
			}
			
			res.close();
			
			sql = "SELECT l.* FROM match_lineups l INNER JOIN matches m ON l.match_id=m.id " +
					"WHERE club_id=" + clubId + " AND m.id IN (SELECT id FROM matches WHERE matchdate > '" + dateStartedAtClub + 
					"' AND (hometeamid=" + clubId + " OR awayteamid=" + clubId + ") ORDER BY matchdate DESC LIMIT " + numberOfRecentMatches + ")";
			
			res = stmt.executeQuery(sql);
			
			while (res.next()){
				possibleMatches++;
				
				for (int i = 1; i < 12; i++)
					if (res.getInt("pl" + i + "id") == personId)
						matchesStarted++;
			}
			
			res.close();
			
			if (possibleMatches > 0)
				result = matchesStarted * 100 / possibleMatches;
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	/**
	 * Sends a negotiated offer back to a club with a message to the club owner
	 */
	public static void playerNegotiateContract(Contract contract, String message, int recipientId){

		String sql = "";

		try {
			sql = "UPDATE contracts SET offered=false, weeklywage="+contract.wage + ", signOnFee="+contract.signOnFee +
					", enddate="+Hjaelper.dateToSQLString(contract.endDate) + ", minimumreleaseclause=" + contract.minRelease +
					", negotiations=negotiations+1 WHERE id="+contract.id;
			stmt.executeQuery(sql);
			sendMessage(recipientId, 99, "Contract negotiation", message.replace("'", "''"));

		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Returns an int containing the avg. talent of players from a given country
	 */
	public static int getCountryAvgTalent(int countryId){

		int result = 0;
		String sql = "SELECT avg_talent FROM countries WHERE id=" + countryId + ";";

		try {
			ResultSet res = stmt.executeQuery(sql);

			if (res.next()){
				result = res.getInt("avg_talent");
			}
			res.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}		

	/**
	 * Returns a resultset containing agents and days since their last login
	 */
	public static HashMap<Integer, Integer> getAgentLastLogon(){
		
		HashMap<Integer, Integer> result = new HashMap<Integer, Integer>();
		ResultSet res = null;
		String sql = "SELECT id, (EXTRACT(epoch FROM age(to_timestamp(login)))/86400)::int as login FROM users WHERE id <> 99 AND id IN (SELECT user_id FROM persons);";

		try {
			res = stmt.executeQuery(sql);

			while (res.next()){
				result.put(res.getInt("id"), res.getInt("login"));
			}
			res.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}
	
	/**
	 * Returns a resultset containing agents and number of players they have
	 */
	public static HashMap<Integer, Integer> getAgentNumberOfPlayers(){
		
		HashMap<Integer, Integer> result = new HashMap<Integer, Integer>();
		ResultSet res = null;
		String sql = "SELECT id, (SELECT count(*) FROM persons WHERE user_id=u.id) as count FROM users u WHERE id <> 99;";

		try {
			res = stmt.executeQuery(sql);

			while (res.next()){
				result.put(res.getInt("id"), res.getInt("count"));
			}
			res.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}


	/**
	 * Returns a resultset containing agents and days since their last login
	 */
	public static ArrayList<Player> getGrowingPlayers(){
		ArrayList<Player> result = new ArrayList<Player>();

		ResultSet res = null;
		String sql = "SELECT * FROM PERSONS WHERE height < final_height;";

		try {
			res = stmt.executeQuery(sql);

			while (res.next()){
				Player p = new Player();
				p.setId(res.getInt("id"));
				p.finalHeight = res.getInt("final_height");
				p.height = res.getInt("height");
				p.age = res.getInt("age");
				result.add(p);
			}
			res.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	/**
	 * Adds 1 centimeter to a players height
	 */
	public static void growPlayer(int personId){
		String sql = "UPDATE PERSONS SET height=height+1 WHERE id=" + personId;

		try {
			stmt.execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Creates a new player
	 */
	public static int createPlayer(Player player, int finalHeight, double[] potential, boolean keeper){

		int result = -1;

		try {
			con.setAutoCommit(false);

			int trainingregime = 1;
			if (keeper) trainingregime = 7;
			
			String sql = "INSERT INTO persons(user_id, firstname, lastname, age, acceleration, topspeed, " + 
					"dribbling, marking, energy, strength, tackling, agility, reaction, " + 
					"handling, shooting, shotpower, passing, technique, height, jumping, " + 
					"stamina, shirtnumber, heading, money, retired, playerpoints, " + 
					"npc, created, commandofarea, shotstopping, rushingout, vision, " + 
					"morale, training_regime_id, country_id, agent_loyalty, final_height) " +
					"VALUES (99, '" + player.getFirstname() + "', '" + player.getLastname() + "', " + player.getAge() + ", " + player.acceleration + ", " + player.topSpeed + ", " + 
					player.dribbling + ", " + player.marking + ", 100, " + player.strength + ", " + player.tackling + ", " + player.agility + ", " + player.reaction + ", " + 
					player.handling + ", " + player.shooting + ", " + player.shotpower + ", " + player.passing + ", " + player.technique + ", " + player.height + ", " + player.jumping + ", " +
					player.stamina + ", 0, " + player.heading + ", 0, false, 0, " +
					"true, now(), " + player.commandOfArea + ", " + player.shotstopping + ", " + player.rushingout + ", " + player.vision + ", " +
					"50, " + trainingregime + ", " + player.countryId + ", " + player.agentLoyalty + ", " + finalHeight + ");";

			stmt.execute(sql, Statement.RETURN_GENERATED_KEYS);
			ResultSet resid = stmt.getGeneratedKeys();
			if (resid.next()){
				int id = resid.getInt(1);
				result = id;

				for (int i = 0; i < potential.length; i++){
					sql = "INSERT INTO player_potentials (person_id, age, basic_pp) VALUES (" + id + ", " + (i+14) + ", " + potential[i] + ");";
					stmt.execute(sql);
				}
			}
			resid.close();

			con.commit();
			con.setAutoCommit(true);

		} catch (SQLException e) {
			try {
				con.rollback();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}

			e.printStackTrace();
		}

		return result;
	}


	/**
	 * Removes a user as the agent of a player and sends a message to the user
	 */
	public static void fireAgent(int userId, int personId, String personFirstname, String personLastname){

		try {
			sendMessage(userId, 99, "Contract with " + personLastname + " cancelled", "The FA has received a letter from " + personFirstname + " " + personLastname + " requesting that you no longer represent him as his agent. Your contract with the player has been cancelled according to the relevant clauses.");
			stmt.execute("UPDATE persons SET user_id=99 WHERE id=" + personId);
			stmt.execute("UPDATE agent_contracts SET cancelled=now() WHERE cancelled IS NULL AND person_id=" + personId);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Removes a user as club owner if he hasn't logged on for 73 days
	 */
	public static void kickClubOwners(){

		try {
			
			Statement stmt2 = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			String sql = "SELECT c.id as club_id, u.id as user_id, clubname, (EXTRACT(epoch FROM age(to_timestamp(login)))/86400)::int as login FROM users u INNER JOIN clubs c ON u.id=c.user_id WHERE u.id <> 99";
			ResultSet res = stmt2.executeQuery(sql);
			while (res.next()){

				if (res.getInt("login") > 73){
					sendMessage(res.getInt("user_id"), 99, "Ownership of " + res.getString("clubname") + " cancelled", "The FA hereby informs you that the board of directors at " + res.getString("clubname") + " have decided to cancel your ownership of the club according to FA directives. This step has been taken as a consequence of your absence and general negligence in club matters.");
					stmt.execute("UPDATE clubs SET user_id=99 where id=" + res.getInt("club_id"));
				}
			}
			res.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		System.out.println("Inactive club owners have been kicked");
	}
	
	/*
	 * Retires a player ending his contract, agent_contract and removing him from lineups 
	 * 
	 */
	public static void retirePlayer(int personId){
		try {
			
			//Set retired
			String sql = "UPDATE persons SET retired = true WHERE id = " + personId;
			stmt.execute(sql);
			
			//Delete contract offers
			sql = "DELETE FROM contracts WHERE person_id = " + personId + " and acceptedbyplayer = false";
			stmt.execute(sql);
			
			//Delete agent offers
			sql = "DELETE FROM agent_offers WHERE person_id = " + personId + " and accepted is null";
			stmt.execute(sql);
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * Returns a list of ID's on active persons who haven't had a contract in more than 3 seasons
	 */
	public static ArrayList<Integer> getPlayersWithoutContractIn3Seasons(){
		String sql = "SELECT id FROM persons p WHERE retired=false AND age > 21*365 and (SELECT max(enddate) FROM contracts WHERE person_id=p.id) < now() - interval '219 days'";
		ArrayList<Integer> result = new ArrayList<Integer>();
		
		try {
			ResultSet res = stmt.executeQuery(sql);
			while (res.next()){

				result.add(res.getInt("id"));
			}
			res.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	/**
	 * Removes a user as the agent of a all his players and sends a message to the user
	 */
	public static void fireAgentFromAllPlayers(int userId){

		try {
			
			Statement stmt2 = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			String sql = "SELECT * FROM persons WHERE user_id=" + userId;
			ResultSet res = stmt2.executeQuery(sql);
			while (res.next()){

				fireAgent(res.getInt("user_id"), res.getInt("id"), res.getString("firstname"), res.getString("lastname"));
			}
			res.close();
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Updates agent_opinion for a single player
	 */
	public static void updateAgentOpinion(int userId, int personId, double opinion, String thoughts, boolean appendThought){
		
		try {
			opinion = Hjaelper.round(opinion, 2);
			
			if (appendThought){
				String sql = "UPDATE agent_opinions SET thoughts=thoughts || ' " + thoughts + "', opinion=" + opinion + " WHERE user_id=" + userId +  " AND person_id = " + personId;
				stmt.execute(sql, Statement.RETURN_GENERATED_KEYS);
			}
			else{
				stmt.execute("UPDATE agent_opinions SET thoughts='" + thoughts + "', opinion=" + opinion + " WHERE user_id=" + userId +  " AND person_id = " + personId);
			}
			
			int count = 0; 
					
			ResultSet resid = stmt.getGeneratedKeys();
			if (resid.next()){
				count = resid.getInt(1);
			}
			resid.close();
			
			if (count == 0){
				String sql = "INSERT INTO agent_opinions (user_id, person_id, opinion, thoughts) VALUES (" + userId + ", " + personId + ", " + opinion + ", '" + thoughts + "')";
				stmt.execute(sql);
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Sets all agent opinion thoughts to an empty string
	 */
	public static void clearAllAgentOpinionThoughts(){
		
		try {
				stmt.execute("UPDATE agent_opinions SET thoughts=''");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Updates agent_opinion for each player who is represented by the agent (user)
	 */
	public static void updateAgentAllPlayersOpinions(int userId, double change, String thoughts, boolean appendThought){
		
		try {
			//Get all players represented by the agent
			ResultSet res = stmt.executeQuery("SELECT id FROM persons p WHERE id IN (SELECT id FROM persons WHERE user_id=" + userId + ") AND (SELECT count(*) FROM agent_opinions WHERE user_id=" + userId + " AND person_id=p.id) = 0");	
			ArrayList<Integer> noOpinion = new ArrayList<Integer>();
			while (res.next()){
				noOpinion.add(res.getInt("id"));
			}
			res.close();
			
			change = Hjaelper.round(change, 2);
			String changeString = "+"+change;
			if (change < 0) changeString = ""+change;
			
			if (appendThought){
				stmt.execute("UPDATE agent_opinions SET thoughts=thoughts || ' " + thoughts + "', opinion=opinion" + changeString + " WHERE user_id=" + userId +  " AND person_id IN (SELECT id FROM persons WHERE user_id=" + userId + ")");
			}
			else{
				stmt.execute("UPDATE agent_opinions SET thoughts='" + thoughts + "', opinion=opinion" + changeString + " WHERE user_id=" + userId +  " AND person_id IN (SELECT id FROM persons WHERE user_id=" + userId + ")");
			}
			
			for (int id : noOpinion){
				String sql = "INSERT INTO agent_opinions (user_id, person_id, opinion, thoughts) VALUES (" + userId + ", " + id + ", 50" + changeString + ", '" + thoughts + "')";
				stmt.execute(sql);
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @param stmt statement to use
	 * @return a resultset with all players (if not retired), their morale, their avg rating, their expected wage, wage and league_id
	 * TODO: Players shouldn't complain about their wages if their contract was signed less than half a season ago
	 */
	public static ResultSet getAllPlayersMoraleRatingsWage(Statement stmt){
		String sql = "select p.id, morale, user_id, COALESCE((SELECT opinion FROM agent_opinions WHERE user_id=p.user_id AND person_id=p.id), 50) as opinion, " +
				"(SELECT count(*) FROM agent_opinions WHERE user_id=p.user_id AND person_id=p.id) as opinionCount, avg_match_rating_normalized, " +
				"(SELECT weeklywage FROM contracts WHERE enddate > now() AND acceptedbyplayer=true AND person_id=p.id) as wage, s.wage as avg_wage " +
				"FROM persons p INNER JOIN player_ratings r ON p.id=r.person_id INNER JOIN wage_stats s ON r.avg_match_rating_normalized=s.rating WHERE retired = false";

		try {
			return stmt.executeQuery(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

	
	
	
	
	/**
	 * Returns an arraylist of all players with an agent currently on a contract. The player object contains the players wage, expected wage and agent opinion
	 */
	public static ArrayList<Player> getAllPlayersOnContracts(){
		ArrayList<Player> result = new ArrayList<Player>();
		
		String sql = "select p.id, morale, p.user_id, " +
"COALESCE((SELECT opinion FROM agent_opinions WHERE user_id=p.user_id AND person_id=p.id), 50) as opinion, cl.league_id, weeklywage as wage, " + 
"wage as avg_wage " +
"FROM persons p  " +
"INNER JOIN contracts c ON enddate > now() AND acceptedbyplayer=true AND c.person_id=p.id " + 
"INNER JOIN player_ratings r ON p.id=r.person_id " +
"INNER JOIN wage_stats s ON r.avg_match_rating_normalized=s.rating " +
"INNER JOIN clubs cl ON cl.id=c.club_id WHERE p.user_id <> 99 AND retired = false";
		
		ResultSet res = null;
		try {
			res = stmt.executeQuery(sql);
			
			while (res.next()){
				Player p = new Player();
				p.setId(res.getInt("id"));
				p.morale = res.getInt("morale");
				p.agentId = res.getInt("user_id");
				p.agentOpinion = res.getInt("opinion");
				p.leagueId = res.getInt("league_id");
				p.wage = res.getInt("wage");
				p.expectedWage = res.getInt("avg_wage");
				result.add(p);
			}
			
			res.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		finally{
			try {
				if (!res.isClosed()) res.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return result;
	}
	
	/**
	 * Updates an agents reputation
	 */
	public static void updateAgentReputation(double repChange, int userId){
		
		try{
			String sql = "UPDATE users SET agent_reputation = agent_reputation + " + Hjaelper.round(repChange, 2) + " WHERE id = " + userId;
			stmt.execute(sql);
			
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Limits agent opinions to numbers between 0 and 100
	 */
	public static void limitAgentOpinion(){
		
		try{
			String sql = "UPDATE agent_opinions SET opinion = 0 WHERE opinion < 0";
			stmt.execute(sql);
			sql = "UPDATE agent_opinions SET opinion = 100 WHERE opinion > 100";
			stmt.execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Limits agent reputations to numbers between 0 and 100
	 */
	public static void limitAgentReputation(){
		
		try{
			String sql = "UPDATE users SET agent_reputation = 0 WHERE agent_reputation < 0";
			stmt.execute(sql);
			sql = "UPDATE users SET agent_reputation = 100 WHERE agent_reputation > 100";
			stmt.execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * @param stmt Statement to use
	 * @return wage statistics for each league.
	 */
	public static ResultSet getWageStatistics(){
		String sql = "SELECT rating, wage FROM wage_stats WHERE season_id = (SELECT MAX(season_id) FROM wage_stats)";

		try {
			return stmt.executeQuery(sql);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}


	/**
	 * Method that sets the morale and agent opinions for players based on wages, random events 
	 */
	public static void updateMorale(){
		int id= 0;
		try {
			con.setAutoCommit(false);
			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			Statement stmt2 = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			String sql = "";	

			//Get all players, their morale, expected wage
			ResultSet res = getAllPlayersMoraleRatingsWage(stmt);			

			while (res.next()){ //Check each player
				double change = 0;
				id = res.getInt("id");
				double morale = res.getDouble("morale");
				//				System.out.println("Morale: " + morale);

				//A bit random is thrown in, as everyone can have good and bad days and once in a while we have big events that change our lives
				Random r = new Random();
				int randomChange = r.nextInt(10) - 5;
				if(r.nextInt(200) == 1){
					randomChange += r.nextInt(90) - 45;
				}

				//				System.out.println("randomChange: " + randomChange);
				//				System.out.println("After random: " + calcNewMorale(morale, randomChange));
				change += randomChange;


				sql = PlayerMorale.getRandomMoraleMessage(id, randomChange);
				if(sql != "") stmt2.execute(sql);


				//If the player has an active contract check the wage
				if (res.getObject("wage") != null){

					double wageChange = wageDifferenceToMorale(res.getInt("wage"), res.getInt("avg_wage"));
					String opinionThoughts = PlayerMorale.getWageMoraleThoughts(wageChange);

					sql = PlayerMorale.getWageMoraleMessageSql(id, wageChange, opinionThoughts);
					if(sql != "") stmt2.execute(sql);

					change += wageChange;
					morale = PlayerMorale.calcNewMorale(morale, change);

					sql = "UPDATE persons SET morale = " + morale + " WHERE id = " + id;
					stmt2.execute(sql);
				}
			}

			con.commit();
			System.out.println("Morale updated");

			//Deactivate old or replaced person_thoughts
			sql = "UPDATE person_thoughts t SET active=false WHERE active=true AND date < now() - interval '2 day'";
			stmt2.execute(sql);
			con.commit();
			System.out.println("Old and replaced person_thoughts deactivated");

			stmt.close();
			stmt2.close();
			con.setAutoCommit(true);
			res.close();
		} catch (SQLException e) {
			try {
				con.rollback();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
			System.out.println(e.getStackTrace());
			System.out.println(e.getMessage());
			System.out.println("Last id: " + id);
		}
	}


	public static void executeSimpleStatement(String statement) throws SQLException{

		if (stmt.isClosed())
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

		try {
			stmt.execute(statement);
		} catch (PSQLException psql){
			psql.printStackTrace();
		} 

		//		stmt.close();
	}

	public static int[] getStadiumAndTicketInfo(int clubid){
		int[] result = new int[4];

		Statement stmt;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			String sql = "SELECT * FROM clubs c INNER JOIN stadiums s ON s.id=c.stadium_id WHERE c.id=" + clubid;
			ResultSet res = stmt.executeQuery(sql);

			if (res.next()){
				result[0] = res.getInt("seatPrice");
				result[1] = res.getInt("standPrice");
				result[2] = res.getInt("seats");
				result[3] = res.getInt("terraces");
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return result;
	}


	/**
	 * @param includeCurrentActivePlayers boolean that specifies whether the current number of active players from each country should be included in the result.
	 * @return An arraylist of countries.
	 */
	public static ArrayList<Country> getAllCountries(boolean includeCurrentActivePlayers){

		ArrayList<Country> result = new ArrayList<Country>();

		Statement stmt;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			String sql = "SELECT * FROM countries";
			if (includeCurrentActivePlayers)
				sql = "SELECT *, (SELECT count(*) FROM persons WHERE country_id=c.id AND retired=false) as currentActivePlayers FROM countries c";

			ResultSet res = stmt.executeQuery(sql);

			while (res.next()){
				if (includeCurrentActivePlayers)
					result.add(new Country(res.getInt("id"), res.getString("name"), res.getInt("active_players"), res.getInt("avg_talent"), res.getInt("currentActivePlayers")));
				else
					result.add(new Country(res.getInt("id"), res.getString("name"), res.getInt("active_players"), res.getInt("avg_talent")));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}


	/**
	 * Returns info on players and their training facilities for all players with potential
	 *  
	 * @return An arraylist of PlayerTrainingInfo.
	 */
	public static ArrayList<PlayerTrainingInfo> getPlayerTrainingInfo(){

		ArrayList<PlayerTrainingInfo> result = new ArrayList<PlayerTrainingInfo>();

		Statement stmt;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			String sql = "select p.*, " +
					"(select trainingfacc from clubs c inner join contracts con on c.id=con.club_id where enddate > now() and person_id=p.id and acceptedbyplayer='t') as trainingfacc, " +
					"COALESCE((select basic_pp from player_potentials where person_id=p.id and age=p.age/365), 0) as basic_pp " +
					"from persons p WHERE id IN (SELECT person_id FROM player_potentials) order by id asc";

			ResultSet res = stmt.executeQuery(sql);

			while (res.next()){
				
				Player player = new Player();
				player.setId(res.getInt(1));
				player.acceleration = res.getDouble("acceleration");
				player.topSpeed = res.getDouble("topspeed");
				player.agility = res.getDouble("agility");
				player.strength = res.getDouble("strength");
				player.jumping = res.getDouble("jumping");
				player.reaction = res.getDouble("reaction");
				player.stamina = res.getDouble("stamina");
				player.dribbling = res.getDouble("dribbling");
				player.shooting = res.getDouble("shooting");
				player.shotpower = res.getDouble("shotpower");
				player.passing = res.getDouble("passing");
				player.technique = res.getDouble("technique");
				player.vision = res.getDouble("vision");
				player.marking = res.getDouble("marking");
				player.tackling = res.getDouble("tackling");
				player.heading = res.getDouble("heading");
				player.commandOfArea = res.getDouble("commandofarea");
				player.handling = res.getDouble("handling");
				player.rushingout = res.getDouble("rushingout");
				player.shotstopping = res.getDouble("shotstopping");
				
				PlayerTrainingInfo i = new PlayerTrainingInfo(res.getInt("id"), res.getInt("trainingfacc"), res.getDouble("basic_pp"), res.getDouble("totalPPFromXP"), res.getDouble("totalPPFromFacc"), player);
				result.add(i);
			}

		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}


	/**
	 * @return An arraylist of PlayerTrainingInfo.
	 */
	public static void addToPlayerpoints(int personId, double pp, double ppFromFacc){


		Statement stmt;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			String sql = "UPDATE persons SET playerpoints = playerpoints + " + pp + ", totalPPFromFacc = totalPPFromFacc + " + ppFromFacc + " WHERE id=" + personId;
			stmt.execute(sql);

		} catch (SQLException e) {
			e.printStackTrace();
		}

	}


	public static int[] getBonus(int personid){
		int[] result = new int[2];

		Statement stmt;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			String sql = "SELECT * FROM contracts WHERE person_id=" + personid + " AND enddate>now() AND acceptedbyplayer='t'";
			ResultSet res = stmt.executeQuery(sql);

			if (res.next()){
				result[0] = res.getInt("goalbonus");
				result[1] = res.getInt("assistbonus");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	//Alert club owners when the contract of a player from their club is about to run out
	public static void contractAlerts(){

		Statement stmt;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			//Find all active contracts that end in 7 days
			String sql = "SELECT firstname, lastname, clubname, cl.user_id FROM contracts c INNER JOIN clubs cl ON cl.id=c.club_id INNER JOIN persons p on p.id=c.person_id WHERE acceptedbyplayer=true AND enddate-current_date = 14";
			ResultSet res = stmt.executeQuery(sql);

			while (res.next()) {
				String msg = res.getString("firstname") + " " + res.getString("lastname") + " is on a contract that expires in 14 days. " +
						"If you would like to keep the player at " + res.getString("clubname") + " you should start negotiating a new contract as soon as possible.";

				sendMessage(res.getInt("user_id"), 99, "Contract expiration", msg);
			}
			res.close();
			stmt.close();

			System.out.println("Contract alerts sent");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	//Alert club owners when their starting 11 is missing a player
	public static void lineupAlerts(){

		Statement stmt;
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			//Find all lineups where the starting 11 is missing a player
			String sql = "SELECT clubname, user_id FROM lineups l INNER JOIN clubs c on l.club_id=c.id WHERE pl1id=-1 OR pl2id=-1 OR pl3id=-1 OR pl4id=-1 OR pl5id=-1 OR pl6id=-1 OR pl7id=-1 OR pl8id=-1 OR pl9id=-1 OR pl10id=-1 OR pl11id=-1";
			ResultSet res = stmt.executeQuery(sql);

			while (res.next()) {
				String msg = "The starting 11 at " + res.getString("clubname") + " is missing a player. The player may have been sold or his contract may have run out. You should update your team tactics as soon as possible.";

				sendMessage(res.getInt("user_id"), 99, "Player missing from lineup", msg);
			}
			res.close();
			stmt.close();

			System.out.println("Lineup alerts sent");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static Color getColor(int i){

		Color result = Color.BLACK;

		switch (i){

		case 1:
			result = Color.red;
			break;

		case 2:
			result = Color.white;
			break;

		case 3:
			result = Color.blue;
			break;

		}
		return result;
	}
	
	public static ArrayList<Team> loadAllTeams(boolean withoutOwnerOnly) {

		ArrayList<Team> result = new ArrayList<Team>();
		String sql = "SELECT *, random() as r FROM clubs c INNER JOIN leagues l ON l.id=c.league_id";
		if (withoutOwnerOnly) sql += " WHERE c.user_id = 99";
		sql += " ORDER BY r";
		
		ResultSet res;
		
		try {
			res = stmt.executeQuery(sql);

			String navn = "";
			Color color1 = Color.BLACK;
			Color color2 = Color.BLACK;
			int rep;
			int fame;
			int userId = 0;

			while (res.next()) {
				
				navn = res.getString("clubName");
				rep = res.getInt("leagueReputation");
				color1 = Hjaelper.getNewColor(res.getString("firstColor"));
				color2 = Hjaelper.getNewColor(res.getString("secondColor"));
				fame = res.getInt("fame");
				userId = res.getInt("user_id");

				int id = res.getInt(1);
				Team team = new Team(navn, color1, color2, id, null, rep, fame);
				team.leagueId = res.getInt("league_id");
				team.setUserId(userId);
				team.ownerId = userId;
				team.setId(id);
				
				result.add(team);
			}
			
			if (res != null) res.close();

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return result;
	}
	
	private static String getStringFromDate(Date d, boolean includeTime){
		String date = (d.getYear() + 1900) + "-";

		if (d.getMonth() < 9)
			date += "0" + (d.getMonth() + 1);
		else
			date += "" + (d.getMonth() + 1);

		if (d.getDate() < 10)
			date += "-0" + d.getDate();
		else
			date += "-" + d.getDate();

		if (includeTime){
			if (d.getHours() < 10)
				date += " 0" + d.getHours();
			else
				date += " " + d.getHours();	

			if (d.getMinutes() < 10)
				date += ":0" + (d.getMinutes());
			else
				date += ":" + (d.getMinutes());	

			if (d.getSeconds() < 10)
				date += ":0" + d.getSeconds();
			else
				date += ":" + d.getSeconds();	
		}

		return date;
	}

	public static void genFixtureList(int leagueId, int startYear, int startMonth, int startDay, int startHour, int minutesInterval, int season_id){
		Statement stmt;

		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			String sql = "SELECT * FROM clubs WHERE league_id=" + leagueId;
			ResultSet res = stmt.executeQuery(sql);
			Map<Integer, Integer> clubStadium = new HashMap<Integer, Integer>();
			Map<Integer, String> clubName = new HashMap<Integer, String>();
			ArrayList<Integer> index = new ArrayList<Integer>();

			while (res.next()) {
				clubName.put(res.getInt("id"), res.getString("clubname"));
				clubStadium.put(res.getInt("id"), res.getInt("stadium_id"));
				index.add(res.getInt("id"));
				System.out.println(res.getInt("id"));
			}
			res.close();

			//Byt random mellem alle klubber s� vi har tilf�ldig startr�kkef�lge
			Random r = new Random();
			for (Integer i = 0; i < index.size(); i++){
				int q = r.nextInt(index.size());
				Integer temp = index.get(i);
				index.set(i, index.get(q));
				index.set(q, temp);
			}

			//			debug
			//			index.clear();
			//			for (int i = 1; i < 17; i++){
			//				index.add(i);
			//				clubName.put(i, "Klub" + i);
			//			}

			boolean swap = false;

			for (Integer j = 0; j < index.size() - 1; j++){

				ArrayList<Integer> min1 = new ArrayList<Integer>();
				ArrayList<Integer> min2 = new ArrayList<Integer>();

				for (int q = 0; q < index.size() / 2; q++){
					min1.add(q * minutesInterval);
					min2.add(q * minutesInterval);
				}

				for (Integer i = 0; i < index.size() / 2; i++){

					swap = !swap;

					if ((i == 0 || i == 1) && (j%2 == 0)){ //hvis det er f�rste kamp i runden byttes den kun hver anden runde
						swap = !swap;
					}

					//					if (swap) System.out.println("runde " + (j+1) + " kamp " + (i+1) + " byttes.");

					int size = min1.size();
					Date d = new Date(startYear - 1900, startMonth, startDay + j * 2, startHour, min1.remove(r.nextInt(size)));
					int home = index.get(i);
					int away = index.get(index.size() - 1 - i);

					if (!swap){
						//						System.out.println(d.toString() + ":\t" + clubName.get(index.get(i)) + " - " + clubName.get(index.get(index.size() - 1 - i)));
					}
					else {
						//						System.out.println(d.toString() + ":\t" + clubName.get(index.get(index.size() - 1 - i)) + " - " + clubName.get(index.get(i)));
						home = index.get(index.size() - 1 - i);
						away = index.get(i);
					}

					sql = "INSERT INTO matches (status, homeTeamId, awayTeamId, matchDate, stadium_id, homeTeamGoals, " +
							"awayTeamGoals, league_id, season_id) values (0, " + home + ", " + away + ", '" + getStringFromDate(d, true) + "', " + clubStadium.get(home) + ", 0, 0, " + leagueId + ", " + season_id + ");";

					stmt.execute(sql);

					System.out.println(d.toString() + ":\t" + clubName.get(index.get(i)) + " - " + clubName.get(index.get(index.size() - 1 - i)));
					size = min2.size();
					d = new Date(startYear - 1900, startMonth, startDay + j * 2 + (index.size() - 1) * 2 + 4, startHour, min2.remove(r.nextInt(size)));
					System.out.println(d.toString() + ":\t" + clubName.get(index.get(index.size() - 1 - i)) + " - " + clubName.get(index.get(i)));

					sql = "INSERT INTO matches (status, homeTeamId, awayTeamId, matchDate, stadium_id, homeTeamGoals, " +
							"awayTeamGoals, league_id, season_id) values (0, " + away + ", " + home + ", '" + getStringFromDate(d, true) + "', " + clubStadium.get(away) + ", 0, 0, " + leagueId + ", " + season_id + ");";

					stmt.execute(sql);
				}
				System.out.println();
				index.add(1, index.remove(index.size() - 1));
			}

			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void switchDatabases(){
		HashMap<String, String> aendredeKolonner = new HashMap<String, String>();
		HashMap<String, String> nyeKolonner = new HashMap<String, String>(); 

		//users
		aendredeKolonner.clear();
		aendredeKolonner.put("name", "username");
		aendredeKolonner.put("pass", "password");
		aendredeKolonner.put("mail", "email");
		//		switchTables("users", "users", "uid", aendredeKolonner, nyeKolonner);
		System.out.println("users done");


		//stadium
		aendredeKolonner.clear();
		nyeKolonner.clear();
		//		switchTables("me_stadium", "stadiums", "stadiumid", aendredeKolonner, nyeKolonner);
		System.out.println("stadiums done");


		//leagues
		aendredeKolonner.clear();
		nyeKolonner.clear();
		//		switchTables("me_league", "leagues", "leagueid", aendredeKolonner, nyeKolonner);
		System.out.println("leagues done");


		//clubs
		aendredeKolonner.clear();
		nyeKolonner.clear();
		nyeKolonner.put("created", "now()");
		nyeKolonner.put("user_id", "1");
		nyeKolonner.put("shortname", "'FCK'");
		//		switchTables("me_club", "clubs", "clubid", aendredeKolonner, nyeKolonner);
		System.out.println("clubs done");



		//finance_type		
		nyeKolonner.clear();
		aendredeKolonner.clear();
		nyeKolonner.clear();
		//		switchTables("me_finance_type", "finance_types", "type", aendredeKolonner, nyeKolonner);		
		System.out.println("me_finance_type done");


		//club_expenses
		nyeKolonner.clear();
		aendredeKolonner.clear();
		aendredeKolonner.put("clubid", "club_id");
		//		switchTables("me_club_expense", "club_expenses", "id", aendredeKolonner, nyeKolonner);		
		System.out.println("clubs_expenses done");


		//club_incomes
		nyeKolonner.clear();
		aendredeKolonner.clear();
		aendredeKolonner.put("clubid", "club_id");
		//		switchTables("me_club_income", "club_incomes", "id", aendredeKolonner, nyeKolonner);		
		System.out.println("clubs_income done");


		//colors
		aendredeKolonner.clear();
		//		switchTables("me_colors", "colors", "colorid", aendredeKolonner, nyeKolonner);
		System.out.println("me_colors done");


		//person
		aendredeKolonner.clear();
		nyeKolonner.clear();
		//		switchTables("me_person", "persons", "personid", aendredeKolonner, nyeKolonner);
		System.out.println("persons done");


		//contract_roles
		aendredeKolonner.clear();
		nyeKolonner.clear();
		//		switchTables("me_contract_role", "contract_roles", "roleid", aendredeKolonner, nyeKolonner);
		System.out.println("contracts_role done");

		//contract
		aendredeKolonner.clear();
		aendredeKolonner.put("clubid", "club_id");
		aendredeKolonner.put("personid", "person_id");
		nyeKolonner.clear();
		//		switchTables("me_contract", "contracts", "contractid", aendredeKolonner, nyeKolonner);
		System.out.println("contracts done");

		//constructions
		aendredeKolonner.clear();
		nyeKolonner.clear();
		aendredeKolonner.put("clubid", "club_id");
		//		switchTables("me_construction", "constructions", "constructionid", aendredeKolonner, nyeKolonner);
		System.out.println("constructions done");

		//friendly_inv
		aendredeKolonner.clear();
		nyeKolonner.clear();
		//		switchTables("me_friendly_inv", "friendly_inv", "finvid", aendredeKolonner, nyeKolonner);
		System.out.println("me_friendly_inv done");

		//lineups
		aendredeKolonner.clear();
		nyeKolonner.clear();
		aendredeKolonner.put("clubid", "club_id");
		//		switchTables("me_lineup", "lineups", "lineupid", aendredeKolonner, nyeKolonner);
		System.out.println("me_lineup done");

		//match
		aendredeKolonner.clear();
		nyeKolonner.clear();
		aendredeKolonner.put("stadiumid", "stadium_id");
		aendredeKolonner.put("leagueid", "league_id");
		//		switchTables("me_match", "matches", "matchid", aendredeKolonner, nyeKolonner);
		System.out.println("matches done");

		//matchlineups
		aendredeKolonner.clear();
		nyeKolonner.clear();
		aendredeKolonner.put("clubid", "club_id");
		aendredeKolonner.put("matchid", "match_id");
		//		switchTables("me_match_lineup", "match_lineups", "mlid", aendredeKolonner, nyeKolonner);
		System.out.println("me_lineup done");

		//traits
		aendredeKolonner.clear();
		nyeKolonner.clear();
		//		switchTables("me_trait", "traits", "traitid", aendredeKolonner, nyeKolonner);
		System.out.println("me_trait done");

		//teamtactics
		aendredeKolonner.clear();
		nyeKolonner.clear();
		aendredeKolonner.put("clubid", "club_id");
		//		switchTables("me_teamtactics", "teamtactics", "ttid", aendredeKolonner, nyeKolonner);
		System.out.println("me_teamtactics done");

		//seasons
		aendredeKolonner.clear();
		nyeKolonner.clear();
		//		switchTables("me_season", "seasons", "seasonid", aendredeKolonner, nyeKolonner);
		System.out.println("me_season done");

		//playertactics
		aendredeKolonner.clear();
		nyeKolonner.clear();
		aendredeKolonner.put("personid", "person_id");
		//		switchTables("me_playertactics", "playertactics", "ptid", aendredeKolonner, nyeKolonner);
		System.out.println("me_playertactics done");

		//me_persons_trait
		aendredeKolonner.clear();
		nyeKolonner.clear();
		aendredeKolonner.put("personid", "person_id");
		aendredeKolonner.put("traitid", "trait_id");
		//		switchTables("me_person_trait", "person_trait", "ptid", aendredeKolonner, nyeKolonner);
		System.out.println("persons_trait done");

		//matchstats
		aendredeKolonner.clear();
		nyeKolonner.clear();
		aendredeKolonner.put("clubid", "club_id");
		aendredeKolonner.put("matchid", "match_id");
		//		switchTables("me_matchstats", "matchstats", "msid", aendredeKolonner, nyeKolonner);
		System.out.println("matchesstats done");

		//match_playerstats
		aendredeKolonner.clear();
		nyeKolonner.clear();
		aendredeKolonner.put("personid", "person_id");
		aendredeKolonner.put("matchid", "match_id");
		//		switchTables("me_match_playerstats", "match_playerstats", "mpid", aendredeKolonner, nyeKolonner);
		System.out.println("matches_playerstats done");

		//match_teamtactics
		aendredeKolonner.clear();
		nyeKolonner.clear();
		aendredeKolonner.put("clubid", "club_id");
		//		switchTables("me_match_teamtactics", "match_teamtactics", "ttid", aendredeKolonner, nyeKolonner);
		System.out.println("matches_teamtactics done");

		//match_playertactics
		aendredeKolonner.clear();
		nyeKolonner.clear();
		aendredeKolonner.put("personid", "person_id");
		//		switchTables("me_match_playertactics", "match_playertactics", "mpid", aendredeKolonner, nyeKolonner);
		System.out.println("matches_playertactics done");

		System.out.println("All done.");
	}

	public static void switchTables(String gammelTabel, String nyTabel, String IDKolonne, HashMap<String, String> AendredeKolonner, HashMap<String, String> NKolonner){
		Statement stmt;
		Statement stmt2 = null;
		Statement stmt3 = null;
		//		String url = "jdbc:postgresql://localhost/cake_footiesite?user=footieman&password=Lommen";
		String url = "jdbc:postgresql://localhost/cake_footiesite?user=postgres&password=Lommen";
		try{
			Connection concake = DriverManager.getConnection(url);
			stmt2 = concake.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
			stmt3 = concake.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
		}catch (SQLException e) {
			e.printStackTrace();
		}
		try{
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

			String sql = "";
			//			String sql = "TRUNCATE " + nyTabel + " CASCADE;";
			//			stmt2.execute(sql);

			sql = "SELECT * FROM " + gammelTabel + ";";
			ResultSet res = stmt.executeQuery(sql);
			ResultSet resKtjek = stmt3.executeQuery("SELECT * FROM " + nyTabel + ";");
			ResultSetMetaData rmd2 = resKtjek.getMetaData();
			ArrayList<String> nyeKOlonner = new ArrayList<String>();
			for (int i = 1; i <= rmd2.getColumnCount(); i++)
				nyeKOlonner.add(rmd2.getColumnName(i));

			ResultSetMetaData rmd = res.getMetaData();
			int cols = rmd.getColumnCount();

			while (res.next()) {

				try{
					sql = "INSERT INTO " + nyTabel + " (id, ";
					for (int i = 1; i <= cols; i++){
						if (nyeKOlonner.contains(rmd.getColumnName(i)) && !rmd.getColumnName(i).toLowerCase().equals("id")){
							sql += rmd.getColumnName(i);
							sql += ", ";
						}
					}

					for(String s : AendredeKolonner.values()){
						sql += s + ", ";
					}
					for(String s : NKolonner.keySet()){
						sql += s + ", ";
					}

					sql = sql.substring(0, sql.length() - 2);
					sql += ") values (" + res.getString(IDKolonne) + ", ";

					for (int i = 1; i <= cols; i++){
						if (nyeKOlonner.contains(rmd.getColumnName(i)) && !rmd.getColumnName(i).toLowerCase().equals("id") && rmd.getColumnName(i).toLowerCase() != IDKolonne){

							res.getString(i);
							if (rmd.getColumnType(i) == java.sql.Types.VARCHAR || rmd.getColumnType(i) == java.sql.Types.DATE || rmd.getColumnType(i) == java.sql.Types.TIMESTAMP || rmd.getColumnType(i) == -7)
								if (!res.wasNull()) sql += "'";

							sql += res.getString(i);

							if (rmd.getColumnType(i) == java.sql.Types.VARCHAR || rmd.getColumnType(i) == java.sql.Types.DATE || rmd.getColumnType(i) == java.sql.Types.TIMESTAMP || rmd.getColumnType(i) == -7)
								if (!res.wasNull()) sql += "'";

							sql += ", ";
						}
					}

					for(String s : AendredeKolonner.keySet()){
						int columnID = -1;
						for (int i = 1; i <= cols; i++){
							if (rmd.getColumnName(i).toLowerCase().equals(s.toLowerCase())){
								columnID = i;
							}
						}
						if (columnID == -1)
							System.out.println(s + " -kolonnen kunne ikke findes");


						res.getString(s);
						if (rmd.getColumnType(columnID) == java.sql.Types.VARCHAR || rmd.getColumnType(columnID) == java.sql.Types.DATE || rmd.getColumnType(columnID) == java.sql.Types.TIMESTAMP || rmd.getColumnType(columnID) == -7
								&& !res.getString(s).equals("null"))
							if (!res.wasNull()) sql += "'";

						sql += res.getString(s);

						if (rmd.getColumnType(columnID) == java.sql.Types.VARCHAR || rmd.getColumnType(columnID) == java.sql.Types.DATE || rmd.getColumnType(columnID) == java.sql.Types.TIMESTAMP || rmd.getColumnType(columnID) == -7
								&& !res.getString(s).equals("null")){
							if (!res.wasNull()) sql += "'";
						}

						sql += ", ";
					}
					for(String s : NKolonner.values()){
						sql += s + ", ";
					}

					sql = sql.substring(0, sql.length() - 2);
					sql += ");";

					System.out.println(sql);
					stmt2.execute(sql);
				}
				catch (Exception e){
					e.printStackTrace();
				}
			}
			res.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public enum MatchEventType{
		GOAL, OWNGOAL, YELLOWCARD, REDCARD
	}
}
