package examplepackage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import agents.bayesianopponentmodel.BayesianOpponentModel;
import negotiator.Agent;
import negotiator.AgentID;
import negotiator.Bid;
import negotiator.BidIterator;
import negotiator.Timeline;
import negotiator.actions.Accept;
import negotiator.actions.Action;
import negotiator.actions.EndNegotiation;
import negotiator.actions.Offer;
import examplepackage.Appraisal;

/**
 * @author M. Shayganfar
 * Some improvements over the standard SimpleAgent.
 * 
 * Random Walker, Affective Intelligence Agent
 */
public class AffectiveAgent extends Agent
{
	private enum Emotions {WORRIEDNESS, HAPPY, SAD, ANGER, SURPRISE};
	public enum AgentLabel {A, B};
	public enum EvaluationType {BATNA, FAIR, MAX};
	
	private Action opponentLastAction = null;
	private Action selfLastAction  = null;
	
	public List<Bid> opponentBidHistory;
	public List<Bid> selfBidHistory;
	
	private ArrayList<Integer> acceptedOffersCount = new ArrayList<Integer>();
	
	private ArrayList<Integer> totalOffersCountAgentA    = new ArrayList<Integer>();
	private ArrayList<Integer> totalOffersCountAgentB    = new ArrayList<Integer>();
	
	private Bid opponentLastBid = null;
	private Bid selfLastBid     = null;
	
	private AgentLabel agentLabel;
	
	private BayesianOpponentModel fOpponentModel;
	
	private ArrayList<Bid> myPreviousBids;
	
	private Action actionOfPartner=null;
	
	private int turnCount = 0;
	
	private Appraisal appraisal;
	
	/**
	 * init is called when a next session starts with the same opponent.
	 */
	public void init()
	{
		appraisal = new Appraisal();
		
		opponentBidHistory = new ArrayList<Bid>();
		selfBidHistory = new ArrayList<Bid>();
		
		String agentID = readAgentLabel();
		if(!agentID.equals(null)) setAgentID(new AgentID(agentID));
		
		prepareOpponentModel();
	}

	private String readAgentLabel() {
		try {
			File file = new File("agentid.txt");
			if (!file.exists()) {
				file.createNewFile();
				setAgentLabel(AgentLabel.A);
				return "A";
			}
			else {
				if(file.delete()) {
					setAgentLabel(AgentLabel.B);
					return "B";
				}
				else return null;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private void setAgentLabel(AgentLabel agentLabel) {
		this.agentLabel = agentLabel;
	}
	
	private AgentLabel getAgentLabel() {
		return agentLabel;
	}
	
	private void prepareOpponentModel() {
		fOpponentModel = new BayesianOpponentModel(utilitySpace);
	}
	
	private void appraise() throws Exception {
		
//		if(appraisal.isDesirable(utilitySpace, fOpponentModel, opponentLastBid, EvaluationType.FAIR, 0.5))
//			System.out.println("+++++ Expressed Emotion: " + Emotions.HAPPY);
//		else
//			System.out.println("+++++ Expressed Emotion: " + Emotions.SAD);
		
		if (appraisal.isControllable(utilitySpace, opponentLastBid, 0.8, 0.1, 180, 0.1, 0.5))
			System.out.println("+++++ Expressed Emotion: " + Emotions.WORRIEDNESS);
		else
			System.out.println("+++++ Expressed Emotion: " + Emotions.WORRIEDNESS);
	}
	
	private Action proposeInitialBid() throws Exception {
		Bid lBid = null;
		lBid = utilitySpace.getMaxUtilityBid();
		return new Offer(getAgentID(), lBid);
	}
	
	@Override
	public String getVersion() { return "3.1"; }
	
	@Override
	public String getName()
	{
		return "Affective Agent";
	}

	public void ReceiveMessage(Action opponentAction) 
	{
		actionOfPartner = opponentAction;
		
		if (opponentAction instanceof Offer)
		{
			try { 
				
				System.out.println("Affective Agent >>> Opponent's Bid: " + ((Offer)opponentAction).getBid() + " , Utility of Opponent's Bid: " + utilitySpace.getUtility(((Offer)opponentAction).getBid()));
				
				opponentLastBid = ((Offer)opponentAction).getBid();
				opponentBidHistory.add(opponentLastBid);
				
				appraise();
				
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private Bid getNextBid(Bid opponentBid, double acceptableUtilityDistanceThreshold) throws Exception {
//		Bid lBid = null;
//		lBid = utilitySpace.getMaxUtilityBid(); // This can be min or max depending on the strategy!
//		selfLastBid = lBid;
//		return lBid;
		
		BidIterator bidit = new BidIterator(utilitySpace.getDomain());

		if (!bidit.hasNext())
			throw new Exception("The domain does not contain any bids!");
		
		while (bidit.hasNext()) {
			Bid thisBid = bidit.next();
			if (getUtility(thisBid) - getUtility(opponentBid) >= acceptableUtilityDistanceThreshold) {
				return thisBid;
			}
		}
		
		return null;
	}
	
	public Action chooseAction()
	{
		Action action = null;

		setTurnCount(++turnCount);
		
		try {
			if (selfLastAction == null)
				action = proposeInitialBid(); // This shouldn't be max bid. It should be something in between!
			else {
				if(actionOfPartner instanceof Offer)
				{
					Bid opponentBid = ((Offer) actionOfPartner).getBid();
					
					double offeredUtility = utilitySpace.getUtility(opponentBid);
				
					double time = timeline.getTime();
				
					if (isAcceptable(offeredUtility, time))
						action = new Accept(getAgentID());
					else {
						Bid lnextBid = getNextBid(opponentBid, 0.05); 

						if (lnextBid == null)
						{
							System.out.println("Could not make an acceptable offer!");
							action = new EndNegotiation();
							return action;
						}
						else
							action = new Offer(getAgentID(), lnextBid);
					}
				}
			}
			
			selfLastAction = action;
			
			if (action instanceof Offer) selfBidHistory.add(((Offer)action).getBid());
			
			opponentLastAction = actionOfPartner;
			
			if (timeline.getType().equals(Timeline.Type.Time)) {
				sleep(0.005);
			}
			
		} catch (Exception e) {
			System.out.println("Exception in chooseAction:" + e.getMessage());
			e.printStackTrace();
		}
		
		return action;
	}

	private boolean isAcceptable(double offeredUtilFromOpponent, double time) throws Exception
	{
		double P = Paccept(offeredUtilFromOpponent,time);
		if (P > Math.random()) // This shouldn't be random!
			return true;		
		return false;
	}
	
	/**
	 * This function determines the accept probability for an offer.
	 * At t=0 it will prefer high-utility offers.
	 * As t gets closer to 1, it will accept lower utility offers with increasing probability.
	 * it will never accept offers with utility 0.
	 * @param u is the utility 
	 * @param t is the time as fraction of the total available time 
	 * (t=0 at start, and t=1 at end time)
	 * @return the probability of an accept at time t
	 * @throws Exception if you use wrong values for u or t.
	 * 
	 */
	double Paccept(double u, double t1) throws Exception
	{
		double t=t1*t1*t1; // steeper increase when deadline approaches.
		if (u<0 || u>1.05) throw new Exception("utility "+u+" outside [0,1]");
		// normalization may be slightly off, therefore we have a broad boundary up to 1.05
		if (t<0 || t>1) throw new Exception("time "+t+" outside [0,1]");
		if (u>1.) u=1;
		if (t==0.5) return u;
		return (u - 2.*u*t + 2.*(-1. + t + Math.sqrt(sq(-1. + t) + u*(-1. + 2*t))))/(-1. + 2*t);
	}

	double sq(double x) { return x*x; }
	
	public int getAcceptedOffersCount(int turnIndex) {
		return acceptedOffersCount.get(turnIndex);
	}
	
	public int getTotalOffersCount(int turnIndex) {
		
		if(getAgentLabel() == AgentLabel.A) return totalOffersCountAgentA.get(turnIndex);
		else if(getAgentLabel() == AgentLabel.B) return totalOffersCountAgentB.get(turnIndex);
		
		return -1;
	}
	
	private void updateAcceptedOffersCount() throws Exception {
		
		int counter = 0;
		
		for(int i = 1 ; i <= opponentBidHistory.get(opponentBidHistory.size()-1).getIssues().size() ; i++)
		{
			for(int j = 1 ; j <= selfBidHistory.get(selfBidHistory.size()-1).getIssues().size() ; j++)
			{
				if(opponentBidHistory.get(opponentBidHistory.size()-1).getValue(i).equals(selfBidHistory.get(selfBidHistory.size()-1).getValue(j)))
				{
					counter++;
				}
			}
		}
		
		acceptedOffersCount.add(counter);
	}
	
	private void updateTotalOffersCount() {
		totalOffersCountAgentA.add(selfBidHistory.get(selfBidHistory.size()-1).getIssues().size());
	}
	
	public int getTurnCount() {
		return this.turnCount/2;
	}
	
	private void setTurnCount(int turnCount) {
		this.turnCount = turnCount;
	}
}
