package tasks;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

import model.Contract;
import model.Player;
import model.Team;
import model.TransferTarget;

import data.ClubsDAO;
import data.DAOCake;
import data.PositionScoreCalculator;
import data.PositionScoreCalculator.Position;
import data.TransferList;

public class Clubs {

	static long DAY_IN_MS = 1000 * 60 * 60 * 24;
	
	//weekly:
	//DONE: BuyPlayersIfMissing
	
	//nightly:
	//DONE: withdrawUnansweredOffers - if offers are withdrawn, run BuyPlayersIfMissing for the club
	//DONE: negotiateIncomingOffers - 
	//DONE: negotiateSentOffers
	// extendContracts - test
	//transferListPlayers
	
	//TODO:
	//playerAttractionToClub - regn ud hvor interesseret en spiller er i at spille for en klub
	//brug playerAttractionToClub i findTransferTargets og playersReactToContractOffers
	
	
	/*
	 * Randomly lists the weakest player at the club for transfer if no player is already listed and there are more than 15 players at the club
	 */
	public static void transferListPlayer(Team team){

		Random r = new Random();
		if (r.nextDouble() > 0.9){
			
			ArrayList<Contract> contracts = ClubsDAO.getClubCurrentPlayers(team.getId());
			boolean someoneListed = false;
			Contract weakestPlayer = null;
			int playersOldEnough = 0;

			for (Contract c : contracts){

				if (ClubsDAO.getPlayerListPrice(c.person_id) > -1){
					someoneListed = true;
				}

				//Don't list players until they've had a chance to develop
				if (c.player.age > 365 * 22) {
					playersOldEnough++;
					
					if (weakestPlayer == null || c.player.getBestPositionScore() < weakestPlayer.player.getBestPositionScore()){
						weakestPlayer = c;
					}
				}
			}
			
			//Only list players if we have enough experienced players
			if (playersOldEnough > 11){
				if (!someoneListed && weakestPlayer != null && contracts.size() > 15){
					int value = weakestPlayer.player.value;
					if (weakestPlayer.player.age > 365 * 30) value *= 0.5;
					else if (weakestPlayer.player.age > 365 * 24) value *= 0.8;

					TransferList.createNewListing(weakestPlayer.player.getId(), weakestPlayer.id, value, "");
				}
			}
		}
	}

	/*
	 * Sends new offers for players at the club who's contract is about to run out if the player is not transfer listed
	 */
	public static void extendContracts(Team team){
		
		ArrayList<Contract> contracts = ClubsDAO.getClubCurrentPlayers(team.getId());

		//If a contract ends before limit then the club will try to renew it unless the player is listed
		Date limit = new Date(System.currentTimeMillis() + (15 * DAY_IN_MS));
		
		for (Contract c : contracts){
			
			if (c.endDate.before(limit)){
				if (ClubsDAO.getPlayerListPrice(c.person_id) == -1 && !c.extensionOffered){
					//the players contract is about to end and he is not transfer listed
					
					double avgRating = DAOCake.getPlayerAvgRating(c.person_id, true);
					int normalWage = DAOCake.getPlayerAvgWage(avgRating, team.leagueId);
					
					Contract newOffer = new Contract();
					newOffer.club_id = team.getId();
					newOffer.person_id = c.person_id;
					//Offer a three season contract (3 * 73 days)
					newOffer.endDate = new Date(System.currentTimeMillis() + (3 * 73 * DAY_IN_MS));
					newOffer.wage = normalWage;
					newOffer.goalBonus = 0;
					newOffer.assistBonus = 0;
					newOffer.minRelease = -1;
					newOffer.transferFee = 0;
					newOffer.signOnFee = 0;
					
					ClubsDAO.createContractOffer(newOffer, false);
					if (c.player.userId != 99) DAOCake.sendMessage(c.player.userId, 99, "New contract offer", c.player.getFirstname() + " " + c.player.getLastname() + " has received an offer of contract extension from his club.");
				}
			}
		}
	}

	
	/*
	 * Negotiate offers the club has sent. Negotiations with a target current club (if he has one) are
	 * handled here as well as negotiations with the player 
	 */
	public static void negotiateSentOffers(Team team){
		
		ArrayList<Contract> offers = ClubsDAO.getClubCurrentOffers(team.getId(), true);
		Random r = new Random();
		
		for (Contract c : offers){
			
			//Not offered means we have received a counter offer
			if (!c.offered){
				
				//Negotiating with the player
				if (c.acceptedByClub){
					
					//Negotiations are taking too long so we withdraw the offer
					if (c.negotiations > 8){
						if (c.player.userId != 99)
							DAOCake.sendMessage(c.player.userId, 99, "Offer from " + team.getName() + " withdrawn", "The offer from " + team.getName() + " for " + c.player.getLastname() + " has been withdrawn. They felt matters weren't progressing quickly enough and refuse to be drawn into lengthy negotiations.");
						
						ClubsDAO.deleteContract(c.id, c.person_id, c.club_id, -1, true);
					}
					else{
						double avgRating = DAOCake.getPlayerAvgRating(c.player.getId(), true);
						int expectedWage = DAOCake.getPlayerAvgWage(avgRating, team.leagueId);
						
						boolean contractChanged = false;
						
						//The club will accept wages around (random) avg wage for the player
						if (c.wage > expectedWage * (0.9 + r.nextDouble() * 0.2)){
							c.wage = (int)(expectedWage * (0.9 + r.nextDouble() * 0.2));
							contractChanged = true;
						}
						//The club will accept a signonfee of 10% of the transferfee 
						//if it's a free transfer they will accept 20% of the players value
						if (c.transferFee > 0 && c.signOnFee > c.transferFee * 10 / 100){
							c.signOnFee = (int)(c.transferFee * 10 / 100);
							contractChanged = true;
						}
						else if (c.transferFee == 0 && c.signOnFee > c.player.value * 20 / 100){
							c.signOnFee = (int)(c.player.value * 20 / 100);
							contractChanged = true;
						}
						
						//The club will accept goal bonus of 5% of the wages and an assist bonus of 3% of wages
						if (c.goalBonus > c.wage * 5 / 100){
							c.goalBonus = (int)(c.wage * 5 / 100);
							contractChanged = true;
						}
						if (c.assistBonus > c.wage * 3 / 100){
							c.assistBonus = (int)(c.wage * 3 / 100);
							contractChanged = true;
						}
						
						//The club will accept a minimum release clause of 1.5 times the players value or above
						if (c.minRelease > -1 && c.minRelease < c.player.value * 1.5){
							c.minRelease = (int)(c.player.value * 1.5);
							contractChanged = true;
						}
						
						if (c.player.userId != 99){
							if (!contractChanged){
								DAOCake.sendMessage(c.player.userId, 99, team.getName() + " accept contract demands", team.getName() + " have accepted the contract demands for " + c.player.getLastname() + " and sent an offer according to the agreed terms.");
							}
							else{
								DAOCake.sendMessage(c.player.userId, 99, "Contract negotiations with " + team.getName(), team.getName() + " have rejected the contract demands for " + c.player.getLastname() + " but are willing to negotiate. They have sent a revised offer.");
							}
						}
						
						//send new offer
						c.offered = true;
						ClubsDAO.updateContractOffer(c);
					}
				}
				//negotiating with club
				else{
					int currentClubOwner = DAOCake.getPlayerClubOwner(c.person_id);
					
					if (c.negotiations > 4){
						if (currentClubOwner > 0 && currentClubOwner != 99){
							DAOCake.sendMessage(currentClubOwner, 99, "Offer for " + c.player.getLastname() + " withdrawn", "The offer from " + team.getName() + " for " + c.player.getFirstname() + " " + c.player.getLastname() + " has been withdrawn. They felt matters weren't progressing quickly enough and refuse to be drawn into lengthy negotiations.");
						}	
						ClubsDAO.deleteContract(c.id, c.person_id, c.club_id, DAOCake.getPlayerClubId(c.person_id), true);
					}
					else{
						boolean contractChanged = false;
						int listPrice = ClubsDAO.getPlayerListPrice(c.person_id);
						
						//If they demand more than the player is listed at the club will send a new offer matching the list price
						if (listPrice > -1 && c.transferFee > listPrice){
							c.transferFee = listPrice;
							contractChanged = true;
						}
						//If the player isn't listed the club will pay up to 1,5 times the players value
						else if (listPrice == -1){
							c.transferFee = (int)(c.player.value * (1.3 + r.nextDouble() * 0.2));
							contractChanged = true;
						}
						
						if (currentClubOwner > 0 && currentClubOwner != 99){
							if (!contractChanged){
								DAOCake.sendMessage(currentClubOwner, 99, team.getName() + " accept demands", team.getName() + " have accepted the transfer fee demands for " + c.player.getLastname() + " and have sent an offer according to the agreed terms.");
							}
							else{
								DAOCake.sendMessage(currentClubOwner, 99, "Transfer negotiations with " + team.getName(), team.getName() + " have rejected the demands for " + c.player.getLastname() + " but are willing to negotiate. They have sent a revised offer.");
							}
						}
						
						//send new offer
						c.offered = true;
						ClubsDAO.updateContractOffer(c);
					}
				}
			}
		}
	}
	
	/*
	 * Negotiate offers other clubs have sent for players contracted to a club
	 * If an offer is accepted, BuyPlayersIfMissing is run to make sure we fill up the squad
	 */
	public static void negotiateIncomingOffers(Team team){		
		ArrayList<Contract> offers = ClubsDAO.getClubCurrentOffers(team.getId(), false);
		Random r = new Random();
		
		for (Contract c : offers){			
			if (c.offered){
				boolean acceptOffer = false;
				
				//If the offer is good enough the club will accept it no matter who the player is
				if (c.transferFee > c.player.value * (1.3 + r.nextDouble() * 0.2)){
					acceptOffer = true;
				}
				else{
					int listPrice = ClubsDAO.getPlayerListPrice(c.person_id);
					
					//If the player is listed and the list price is met or almost met the offer is accepted
					if (listPrice > -1 && c.transferFee >= listPrice * 0.9){
						acceptOffer = true;
					}
					//If the offer cannot be accepted now we will negotiate it unless negotiations have taken too long
					else{
						if(c.negotiations > 4){
							Team sender = ClubsDAO.loadTeam(c.club_id, null, false, false);
							DAOCake.sendMessage(sender.ownerId, 99, "Offer for " + c.player.getLastname() + " rejected", "Your offer of " + c.transferFee + " for " + c.player.getFirstname() + " " + c.player.getLastname() + " has been rejected and the player's current club have withdrawn from negotiations. They felt matters weren't progressing quickly enough and refuse to be drawn into lengthy negotiations.");
							ClubsDAO.deleteContract(c.id, c.person_id, c.club_id, DAOCake.getPlayerClubId(c.person_id), true);
						}
						else{
							//If the player is listed but the list price isn't nearly met send a negotiated offer
							if (listPrice > -1){
								int oldOffer = c.transferFee;
								c.transferFee = (int)(listPrice * 0.9) + r.nextInt((int)(listPrice * 0.1));
								c.offered = false;
								ClubsDAO.updateContractOffer(c);
								Team sender = ClubsDAO.loadTeam(c.club_id, null, false, false);
								DAOCake.sendMessage(sender.ownerId, 99, "Counter offer for " + c.player.getLastname() + " received", "Your offer of � " + oldOffer + " for " + c.player.getFirstname() + " " + c.player.getLastname() + " has been rejected but the player's current club are willing to negotiate. The player is listed at � " + listPrice + ", but they would be willing to accept an offer of � " + c.transferFee + ".");
							}
							//The player isn't listed and the offer isn't amazing. Negotiate it
							else{
								int oldOffer = c.transferFee;
								c.transferFee = (int)(c.player.value * (1.5 + r.nextDouble() * 0.2));
								c.offered = false;
								ClubsDAO.updateContractOffer(c);
								Team sender = ClubsDAO.loadTeam(c.club_id, null, false, false);
								DAOCake.sendMessage(sender.ownerId, 99, "Counter offer for " + c.player.getLastname() + " received", "Your offer of � " + oldOffer + " for " + c.player.getFirstname() + " " + c.player.getLastname() + " has been rejected but the player's current club are willing to negotiate. They would like to keep the player, but every player has his price and they would be willing to accept an offer of � " + c.transferFee + ".");
							}
						}
					}
				}
				
				if(acceptOffer){
					c.acceptedByClub = true;
					ClubsDAO.updateContractOffer(c);
					Team sender = ClubsDAO.loadTeam(c.club_id, null, false, false);
					DAOCake.sendMessage(sender.ownerId, 99, "Offer for " + c.player.getLastname() + " accepted", "Your offer of " + c.transferFee + " for " + c.player.getFirstname() + " " + c.player.getLastname() + " has been accepted. The player and his agent will now look at the players terms.");
					BuyPlayersIfMissing(team);
				}
			}
		}
		
	}
	
	
	/**
	 * Withdraw offers sent out if no reply was given within seven days or the negotiations are going nowhere
	 */
	public static void withdrawUnansweredOffers(Team team){
		ArrayList<Contract> offers = ClubsDAO.getClubCurrentOffers(team.getId(), true);
		for (Contract c : offers){
			try {
				String MSG = "";
				if (c.negotiations > 4){
					//Send a message to player agent (if negotiating with the player) and current club that the offer has been withdrawn
					MSG = team.getName() + " have decided to withdraw their offer for " + 
							c.player.getFirstname() + " " + c.player.getLastname() + " as they feel " +
							" negotiations are not leading anywhere.";

					int currentClubOwner = DAOCake.getPlayerClubOwner(c.person_id);
					if (currentClubOwner > 0 && currentClubOwner != 99)
						DAOCake.sendMessage(currentClubOwner, 99, team.getName() + " withdraw from negotiations", MSG);

					if (c.acceptedByClub){
						if (c.player.userId > 0 && c.player.userId != 99)
							DAOCake.sendMessage(c.player.userId, 99, team.getName() + " withdraw from negotiations", MSG);
					}

					ClubsDAO.deleteContract(c.id, c.person_id, c.club_id, DAOCake.getPlayerClubId(c.person_id), true);
					Team sender = ClubsDAO.loadTeam(c.club_id, null, false, false);
					BuyPlayersIfMissing(sender);
				}
				else if (c.offered && c.dateOffered.before(new Date(System.currentTimeMillis() - (7 * DAY_IN_MS)))){

					MSG = team.getName() + " have decided to withdraw their offer for " + 
							c.player.getFirstname() + " " + c.player.getLastname() + " as they feel " +
							" negotiations are not progressing quickly enough.";

					int currentClubOwner = DAOCake.getPlayerClubOwner(c.person_id);
					if (currentClubOwner > 0 && currentClubOwner != 99)
						DAOCake.sendMessage(currentClubOwner, 99, team.getName() + " withdraw from negotiations", MSG);

					if (c.acceptedByClub){
						if (c.player.userId > 0 && c.player.userId != 99)
							DAOCake.sendMessage(c.player.userId, 99, team.getName() + " withdraw from negotiations", MSG);
					}

					ClubsDAO.deleteContract(c.id, c.person_id, c.club_id, DAOCake.getPlayerClubId(c.person_id), true);
					Team sender = ClubsDAO.loadTeam(c.club_id, null, false, false);
					BuyPlayersIfMissing(sender);
				}
			} catch (Exception e) {
				System.out.println(e.getStackTrace());
			}
		}
		
	}

	/*
	 * Makes sure the club has at least 17 players - 2 GK, 3 CB, 3 FB, 3 CM, 3 WG, 3 ST
	 * @Param team needs to have id and leagueId set 
	 */
	public static void BuyPlayersIfMissing(Team team){
		ArrayList<Player> players = null;
		PositionScoreCalculator PSC = new PositionScoreCalculator();

		try {
			players = ClubsDAO.loadAllClubPlayers(team);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		//Find out the player types
		for (Player p : players)
			PSC.calculatePositions(p);

		//Find out how many of each type of player we have
		int GK = 0, CB = 0, FB = 0, CM = 0, WG = 0, ST = 0;

		for (Player p : players){
			switch(p.getBestPosition()){
			case GK:
				GK++;
				break;
			case CB:
				CB++;
				break;
			case FB:
				FB++;
				break;
			case CM:
				CM++;
				break;
			case WG:
				WG++;
				break;
			case ST:
				ST++;
				break;
			}
		}

		//Get players we have pending offers for
		ArrayList<Contract> offers = ClubsDAO.getClubCurrentOffers(team.getId(), true);

		//We add each offer that has been accepted by the players current club so we don't buy too many
		for (Contract c : offers){
			PSC.calculatePositions(c.player);
			switch(c.player.getBestPosition()){
			case GK:
				GK++;
				break;
			case CB:
				CB++;
				break;
			case FB:
				FB++;
				break;
			case CM:
				CM++;
				break;
			case WG:
				WG++;
				break;
			case ST:
				ST++;
				break;
			}
		}

		//Find out how many players the club needs to buy so they know how much they can spend on each
		int totalPlayersMissing = 17 - GK - CB - FB - CM - WG - ST;

		if (totalPlayersMissing > 0){

			int remainingTransferBudget = ClubsDAO.getRemainingTransferBudget(team.getId());
			int remainingWageBudget = ClubsDAO.getRemainingWageBudget(team.getId());
			int transferBudgetPerPlayer = remainingTransferBudget / totalPlayersMissing;
			int wageBudgetPerPlayer = remainingWageBudget / totalPlayersMissing;

			//We need to be able to offer at least 100 to get any player
			if (wageBudgetPerPlayer < 200)
				wageBudgetPerPlayer = 200;

			//debug
//			transferBudgetPerPlayer = 20000000;
//			wageBudgetPerPlayer = 5000;

			//Buy GK if we need one and don't have a pending offer for one
			if (GK < 2){
				ArrayList<TransferTarget> targets = ClubsDAO.findPossibleTransferTargets(team.getId(), 3, Position.GK, transferBudgetPerPlayer, wageBudgetPerPlayer);
				TransferTarget bestTarget = null;

				for (TransferTarget tt : targets){
					tt.calcScoreAndPrice(transferBudgetPerPlayer, wageBudgetPerPlayer);
					if (bestTarget == null || bestTarget.targetScore < tt.targetScore){
						bestTarget = tt;
					}
				}

				if (bestTarget != null)
					SendNewOffer(bestTarget, team);
			}

			//Buy CB if we need one and don't have a pending offer for one
			if (CB < 3){
				ArrayList<TransferTarget> targets = ClubsDAO.findPossibleTransferTargets(team.getId(), 3, Position.CB, transferBudgetPerPlayer, wageBudgetPerPlayer);
				TransferTarget bestTarget = null;

				for (TransferTarget tt : targets){
					tt.calcScoreAndPrice(transferBudgetPerPlayer, wageBudgetPerPlayer);
					if (bestTarget == null || bestTarget.targetScore < tt.targetScore){
						bestTarget = tt;
					}
				}

				if (bestTarget != null)
					SendNewOffer(bestTarget, team);
			}

			if (FB < 3){
				ArrayList<TransferTarget> targets = ClubsDAO.findPossibleTransferTargets(team.getId(), 3, Position.FB, transferBudgetPerPlayer, wageBudgetPerPlayer);
				TransferTarget bestTarget = null;

				for (TransferTarget tt : targets){
					tt.calcScoreAndPrice(transferBudgetPerPlayer, wageBudgetPerPlayer);
					if (bestTarget == null || bestTarget.targetScore < tt.targetScore){
						bestTarget = tt;
					}
				}

				if (bestTarget != null)
					SendNewOffer(bestTarget, team);
			}

			if (CM < 3){
				ArrayList<TransferTarget> targets = ClubsDAO.findPossibleTransferTargets(team.getId(), 3, Position.CM, transferBudgetPerPlayer, wageBudgetPerPlayer);
				TransferTarget bestTarget = null;

				for (TransferTarget tt : targets){
					tt.calcScoreAndPrice(transferBudgetPerPlayer, wageBudgetPerPlayer);
					if (bestTarget == null || bestTarget.targetScore < tt.targetScore){
						bestTarget = tt;
					}
				}

				if (bestTarget != null)
					SendNewOffer(bestTarget, team);
			}

			if (WG < 3){
				ArrayList<TransferTarget> targets = ClubsDAO.findPossibleTransferTargets(team.getId(), 3, Position.WG, transferBudgetPerPlayer, wageBudgetPerPlayer);
				TransferTarget bestTarget = null;

				for (TransferTarget tt : targets){
					tt.calcScoreAndPrice(transferBudgetPerPlayer, wageBudgetPerPlayer);
					if (bestTarget == null || bestTarget.targetScore < tt.targetScore){
						bestTarget = tt;
					}
				}

				SendNewOffer(bestTarget, team);
			}

			if (ST < 3){
				ArrayList<TransferTarget> targets = ClubsDAO.findPossibleTransferTargets(team.getId(), 3, Position.ST, transferBudgetPerPlayer, wageBudgetPerPlayer);
				TransferTarget bestTarget = null;

				for (TransferTarget tt : targets){
					tt.calcScoreAndPrice(transferBudgetPerPlayer, wageBudgetPerPlayer);
					if (bestTarget == null || bestTarget.targetScore < tt.targetScore){
						bestTarget = tt;
					}
				}
				if (bestTarget != null)
					SendNewOffer(bestTarget, team);
			}
		}
	}

	private static void SendNewOffer(TransferTarget target, Team team){
		if (target != null){
			//Send offer
			Contract offer = new Contract();
			offer.club_id = team.getId();
			offer.person_id = target.personId;
			//Offer a three season contract (3 * 73 days)
			offer.endDate = new Date(System.currentTimeMillis() + (3 * 73 * DAY_IN_MS));
			offer.wage = (int)(target.expectedWage * 1.05);
			offer.goalBonus = 0;
			offer.assistBonus = 0;
			offer.minRelease = -1;
			offer.transferFee = target.price;
			offer.signOnFee = 0;

			ClubsDAO.createContractOffer(offer, target.hasClub);
			
			if (target.hasClub){
				int owner = DAOCake.getPlayerClubOwner(target.personId);
				if (owner != 99)
					DAOCake.sendMessage(owner, 99, "Offer received", "We have received an offer of � " + offer.transferFee + " for " + target.lastname + ".");
			}
			else{
				if (target.userId != 99)
					DAOCake.sendMessage(target.userId, 99, "Offer received", target.lastname + " has received a contract offer from " + team.getName() + ".");
			}
		}
	}
	
}
