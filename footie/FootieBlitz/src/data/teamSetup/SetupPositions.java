package data.teamSetup;

import java.util.ArrayList;
import java.util.Random;

import model.Bold;
import model.Match;
import model.Pitch;
import model.Player;
import model.PlayerRole;
import model.Settings;
import model.StateHasBall;
import model.StateOppHasBall;
import model.StateTeamHasBall;
import model.Team;

import data.ClubsDAO;
import data.DAOCake;
import data.PositionScoreCalculator;


public class SetupPositions {

	Random r = new Random();
	
	/**
	 * Calculates and sets the different positional scores for all players on a team
	 * @param teamT
	 */
	public void calculatePlayerPositionsScoresForTeam(ArrayList<Player> players){
		PositionScoreCalculator posCalc = new PositionScoreCalculator();
		for(Player p : players){
			posCalc.calculatePositions(p);
		}	
	}
	
	/**
	 * Prepares players positions on the pitch (it is different to their positionX & Y)
	 * Adds the to the team, if they are not added already
	 * @param teamA is this the left sided team?
	 * @param team the team to prepare
	 * @param oppTeam the opposing team
	 * @param match the match
	 * @param settings
	 * @param bold
	 * @param pitch
	 * @param pitchPosX
	 * @param pitchPosY
	 * @param matchId
	 * @param pitchWidth
	 * @param pitchHeight
	 */
	public void preparePlayers(boolean teamA, Team team, Team oppTeam, Match match, Settings settings, 
			Bold bold, Pitch pitch, int pitchPosX, int pitchPosY, int matchId, int pitchWidth, int pitchHeight){
		ArrayList<Player> players = new ArrayList<Player>();
		try{
			players = ClubsDAO.loadAllClubPlayers(team);
			DAOCake.loadPlayerTactics(players);
			int[] lineup = DAOCake.loadLineup(team);
			int nrPlayersInLineup = playersFromClubInStartingLineup(players, lineup);
			
			calculatePlayerPositionsScoresForTeam(players);
			
			//If there are less than 11 players in the lineup, the computer takes over and sets the lineup and tactics
			if(nrPlayersInLineup < 11 || team.getUserId() == 99){
				CreateTeamSheet.creatTeamSheet(CreateTeamSheet.chooseRandomTeamTactic(), team, players);
				//System.out.println("AfterCreateTeamSheet " + team.getName() + " : " + team.getPlayers().size());
			}
			else{
				String[] positions = DAOCake.loadPositions(team);
				setPlayerPositionsOnPitch(players, positions, lineup, pitchPosX, pitchPosY);					
				int[] playerTeamTactics = DAOCake.loadPlayerTeamTactics(team);
				setPlayerTeamTacticsOnTeam(team, players, playerTeamTactics);				
				setPlayerRoles(players, lineup);
				players = fillRestOfSubs(players);
				
				//Set players in lineup order
				players = orderPlayersByLineup(players, lineup);
			}
			
			//mirror teamB's position so they set up on the right side of the pitch
			if(!teamA){
				setTeamBToRightSideOfPitch(players, pitchWidth, pitchHeight);
			}	
			finishTeamSetup(team, players, matchId, settings, match, bold, pitch, oppTeam);	
			setSubstitutePositions(team, teamA, pitchPosX, pitchPosY);
			
		}
		catch (Exception e){
			System.out.println("Exception: " + e.getMessage());
		}		
	}

	/**
	 * Orders the players by lineup - important for tactics on the live site and the keeper needs to be first in case of substitutions
	 * @param players
	 * @param lineup
	 * @return A new arraylist containing all players sorted by lineup order 
	 */
	private ArrayList<Player> orderPlayersByLineup(ArrayList<Player> players, int[] lineup){
		 ArrayList<Player> result = new  ArrayList<Player>();
		 
		 for(int i : lineup){
			 Player foundPlayer = null;
			 
			 for(Player p : players){
				 if (p.getId() == i){
					 foundPlayer = p;
				 }
			 }
			 if (foundPlayer != null){
				 result.add(foundPlayer);
				 players.remove(foundPlayer);
			 }
		 }
		 
		 //Add the rest of the players if they weren't in the lineup
		 result.addAll(players);
		 
		 return result;
	}
	
	/**
	 * Counts the starting eleven player id's to the players in a team.
	 * @param players
	 * @param lineup
	 * @return the number of players in your team that are in the starting lineup (first 11 player ids in the lineup)
	 */
	private int playersFromClubInStartingLineup(ArrayList<Player> players, int[] lineup){
		int nrInLineup = 0;		
		for(Player p : players){
			int firstEleven = 1;
			for(int i : lineup){
				if(firstEleven > 11) break; //Only counts players on the starting lineup (the first eleven on the lineup)
				if(p.getId() == i){
					nrInLineup++;
					break;
				}				
				firstEleven++;
			}			
		}		
		return nrInLineup;
	}
	
	/**
	 *  If a team has all the players in the starting lineup in its club, the players' positions will be set 
	 *  according to their place in the lineup.
	 * @param players
	 * @param positions (892,908, 925,903, 1061,956, 930,1206, 1004,957, 1000,910, 1084,1154, 922,1160)
	 * @param lineup
	 */
	private void setPlayerPositionsOnPitch(ArrayList<Player> players, String[] positions, int[] lineup, int pitchPosX, int pitchPosY){
		Player p = null;
		for (int i = 0; i < 11; i++){
			int id = lineup[i];
			for(Player player : players){
				if(player.getId() == id){
					p = player;
				}
			}			
			String s = positions[i];
			String pos[] = s.split(",");
			p.setStartPosX(Integer.parseInt(pos[0]));
			p.setStartPosY(Integer.parseInt(pos[1]));
			p.setX(p.getStartPosX() + 500);
			p.setY(p.getStartPosY() + 500);
		}
	}
	
	/**
	 * Sets the tactics of the players on a given team, given that teams teamtactics. 
	 * (captain, throwinright, throwinleft, penaltytaker, freekickshort, freekicklong, cornerright, 
	 * cornerleft, targetman)
	 * @param team 
	 * @param players ArrayList of Players
	 * @param playerTeamTactics int[] A list of ids (person_id/player.id) representing which person has which tactic
	 * int[0] = captain, int[1] = throwinright, int[2] = throwinleft, int[3] = penaltytaker, int[4] = freekickshort
	 * int[5] = freekicklong, int[6] = cornerright, int[7] = cornerleft, int[8] = targetman
	 */
	private void setPlayerTeamTacticsOnTeam(Team team, ArrayList<Player> players, int[] playerTeamTactics){
			for(Player p : players){
				if (p.getId() == playerTeamTactics[0]) team.setpCaptain(p);
				if (p.getId() == playerTeamTactics[1]) team.setpThrowinRight(p);
				if (p.getId() == playerTeamTactics[2]) team.setpThrowinLeft(p);
				if (p.getId() == playerTeamTactics[3]) team.setpPenalty(p);
				if (p.getId() == playerTeamTactics[4]) team.setpFreekickShort(p);
				if (p.getId() == playerTeamTactics[5]) team.setpFreekickLong(p);
				if (p.getId() == playerTeamTactics[6]) team.setpCornerRight(p);
				if (p.getId() == playerTeamTactics[7]) team.setpCornerLeft(p);
				if (p.getId() == playerTeamTactics[8]) team.setpTargetMan(p);
		}		
	}
	
	/**
	 * Sets the roles of all players in the lineup.
	 * Only three roles will be given, GK to the first linup id, and CM to the 10 next on the lineup, and SUB to the next 5
	 * @param players
	 * @param lineup
	 */
	private void setPlayerRoles(ArrayList<Player> players, int[] lineup){
		boolean keeperSet = false;
		int lineupNr = 1;
		for(int i : lineup){
			for(Player p : players){
				if(lineupNr < 12){
					if (!keeperSet){
						if(p.getId() == i){
						p.setRole(PlayerRole.GK);
						p.setKeeper(true);
						p.setGuessPenalties(r.nextBoolean());
						System.out.println("keeper: " + p.getShirtNumber());
						keeperSet = true;
						}
					}
					else
						if(p.getId() == i) p.setRole(PlayerRole.CM);
				}
				else if(lineupNr < 19){
					if(p.getId() == i) {
						p.setRole(PlayerRole.SUB);
					}
				}
			}
			lineupNr++;
		}
		
	}
	
	/**
	 * If the lineup is less than 16 strong, we will fill in any leftover players.
	 * All players that are added like this are added as subs
	 * @param players
	 * @return
	 */
	private ArrayList<Player> fillRestOfSubs(ArrayList<Player> players){
		ArrayList<Player> finishedTeam = new ArrayList<Player>();
		
		for(Player p : players){
			if(p.getRole() != null){
				finishedTeam.add(p);
			}
		}
		if(finishedTeam.size() < 18){
			for(Player p : players){
				if(p.getRole() == null && finishedTeam.size() < 16){
					p.setRole(PlayerRole.SUB);
					finishedTeam.add(p);
				}
			}
		}
		
		return finishedTeam;
	}
	
	private void setTeamBToRightSideOfPitch(ArrayList<Player> players, int pitchWidth, int pitchHeight){
		for (Player p : players){
				p.setStartPosX(pitchWidth - p.getStartPosX());
				if (p.getStartPosY() != -20) p.setStartPosY(pitchHeight - p.getStartPosY());			
			}
	}

	private void finishTeamSetup(Team team, ArrayList<Player> players, int matchId, Settings settings, Match match, Bold bold, Pitch pitch, Team oppTeam){
		int subs = 0;
		for(Player p : players){			
			if(p.getRole() != null){
				p.setMyTeam(team);
				p.setSettings(settings);
				p.setMatch(match);
				p.setBold(bold);
				p.setPitch(pitch);
				p.setOppTeam(oppTeam);
				p.setStartPosX(p.getStartPosX() + pitch.getPitchPosX());
				p.setStartPosY(p.getStartPosY() + pitch.getPitchPosY());
				p.setPosX(p.getStartPosX());
				p.setPosY(p.getStartPosY());
				p.x = p.getStartPosX();
				p.y = p.getStartPosY();
				p.setStateHasBall(new StateHasBall(p));
				p.setStateOppHasBall(new StateOppHasBall(p));
				p.setStateTeamHasBall(new StateTeamHasBall(p));
				if (p.getRole() == PlayerRole.SUB && !team.getSubs().contains(p)){
					team.addSub(p);		
					subs++;
				}
				else if(!team.getPlayers().contains(p))
					team.addPlayer(p);
				
				p.setStates();
				DAOCake.addPlayerToPlayerMatchStats( matchId, p.getId(), team.getId(), team.leagueReputation);
			}
		}
	}
	
	private void setSubstitutePositions(Team team, Boolean teamA, int pitchPosX, int pitchPosY){
		int subNr = 0;
		int posX = 380;
		if(!teamA) posX += 90;
		for(Player p : team.getSubs()){
			p.setStartPosX(posX + subNr * 10 + pitchPosX);
			p.setStartPosY(-20 + pitchPosY);
			p.setPosX(posX + subNr * 10 + pitchPosX);
			p.setPosY(-20 + pitchPosY);
			p.setX(posX + subNr * 10 + pitchPosX);
			p.setY(-20 + pitchPosY);
			subNr++;
		}
	}
}
