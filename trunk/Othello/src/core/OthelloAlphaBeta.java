package core;

import java.util.*;

/**
 * 
 */

/**
 * 
 * @author Nicholas Ver Hoeve
 */
public class OthelloAlphaBeta {
	Map<BoardAndDepth, Window> transpositionTable;

	int minDepthToStore = 4;
	int valueOfDraw = -3;
	int maxSearchDepth = 12;
	int levelsToSort = 3;
	
	//int maxTableSize = 
	
	public static final int NOSCORE = 0x80000000;
	public static final int LOWESTSCORE = 0x80000001;
	public static final int HIGHESTSCORE = 0x7FFFFFFF;
	
	public static final int WHITE = 0;
	public static final int BLACK = 1;
	
	//counters
	int leafCount = 0;
	int nodesSearched = 0;
	int nodesRetrieved = 0;
	
	int maxTableEntries = 750000;
	
	protected OthelloBitBoard rootNode = null; // position to begin analysis
	protected int rootNodeTurn; //the current player turn (WHITE or BLACK) for root node
	protected int scoreOfConfiguration = NOSCORE; // score of the last completed scan of this root
	
	/*
	 * This class bundles alpha and beta so the range can be stored in a map
	 */
	public static class Window {
		public int alpha; // lowerbound
		public int beta; // upperbound
		
		Window(Window o) {
			this.alpha = o.alpha;
			this.beta = o.beta;
		}
		Window() {
			this.alpha = LOWESTSCORE;
			this.beta = HIGHESTSCORE;
		}
		Window(int alpha, int beta) {
			this.alpha = alpha;
			this.beta = beta;
		}
	}
	
	/*
	 * This class is needed because the depth of the board when analyzed must
	 * be included and factored into the hashcode.
	 */
	protected static class BoardAndDepth extends OthelloBitBoard {
		int hashMod;
		
		public BoardAndDepth(OthelloBitBoard board, int depth, int turn) {
			white = board.white;
			black = board.black;
			this.hashMod = (depth << 1) | turn;
		}
		
		public int hashCode() {
			return super.hashCode() ^ (hashMod * 136385313);
		}
		
		public boolean equals(Object other) {
			return super.equals(other) && 
				(other instanceof BoardAndDepth) && 
				((BoardAndDepth)other).hashMod == hashMod;
		}
		
		public int getTurn() {
			return hashMod & 1;
		}
		
		public int getDepth() {
			return hashMod >>> 1;
		}
	};
	
	/*
	 * This class is intended to sort optimal ordering
	 */
	protected static class BoardAndWindow implements Comparable<BoardAndWindow>{
		public OthelloBitBoard board;
		public Window window;
		
		public BoardAndWindow(OthelloBitBoard board, Window window) {
			this.board = board;
			this.window = window;
		}
	
		/**
		 * comparator to sort windows. cares most about alpha. sort ascending
		 */
		public int compareTo(BoardAndWindow arg0) {
			int rank = ((window.alpha) >> 1) -((arg0.window.alpha) >> 1);
			rank += ((window.beta) >> 4) - ((arg0.window.beta) >> 4);
			return rank;
		}
	}
	
	/**
	 * construct with custom table size
	 */
	OthelloAlphaBeta(int maxTableEntries) {
		this.maxTableEntries = maxTableEntries;
		transpositionTable = new HashMap<BoardAndDepth, Window>(maxTableEntries / 2, 0.5f);
	}
	
	/**
	 * construct with default table size
	 */
	OthelloAlphaBeta() {
		transpositionTable = new HashMap<BoardAndDepth, Window>(maxTableEntries / 2, 0.5f);
	}
	
	/**
	 * construct with default table size
	 */
	protected OthelloAlphaBeta(Map<BoardAndDepth, Window> existingTable) {
		transpositionTable = existingTable;
	}

	/**
	 * negamax search with Alpha-beta pruning, all features and full window
	 * scans the root node stored in this object
	 * 
	 * @return the value of the best score found
	 */
	public int alphaBetaSearch() {
		return scoreOfConfiguration = alphaBetaSearch(LOWESTSCORE, HIGHESTSCORE);
	}
	
	/**
	 * negamax search with Alpha-beta pruning, all features
	 * scans the root node stored in this object
	 * 
	 * @param alpha : lower bound on the window
	 * @param beta : upper bound on the window
	 * @return the value of the best score found
	 */
	protected int alphaBetaSearch(int alpha, int beta) {
		if (levelsToSort <= 0) {
			if (maxSearchDepth <= minDepthToStore) {
				return alphaBetaNoTable(rootNode, alpha, beta, rootNodeTurn, maxSearchDepth);
			} else {
				return alphaBetaNoSort(rootNode, alpha, beta, rootNodeTurn, maxSearchDepth);
			}
		} else {
			return alphaBetaSorted(rootNode, alpha, beta, rootNodeTurn, maxSearchDepth);
		}
	}
	
	/**
	 * negamax search with Alpha-beta pruning, with transpositions and sorting
	 * intended for near-root searching
	 * 
	 * @param position : current position to analyze
	 * @param alpha : lower bound on the window
	 * @param beta : upper bound on the window
	 * @param turn : current turn (WHITE or BLACK)
	 * @param depth : how deep to recurse
	 * @return the value of the best score found
	 */
	protected int alphaBetaSorted(OthelloBitBoard position, int alpha, int beta, 
			int turn, int depth) {
		BoardAndDepth storedBoard = new BoardAndDepth(position, depth, turn);
		Window storedWindow = transpositionTable.get(storedBoard);
		
		++nodesSearched;
		
		if (storedWindow != null)
		{
			++nodesRetrieved;
			
			//if we know that this stored position
			if (storedWindow.alpha >= beta) {
				return storedWindow.alpha;
			}
			if (storedWindow.beta <= alpha) {
				return storedWindow.beta;
			}
			
			//align windows
			alpha = Math.max(alpha, storedWindow.alpha);
			beta = Math.min(beta, storedWindow.beta);
		} else {
			storedWindow = new Window(); // go ahead and allocate
		}
		
		if (alpha == beta) {
			return alpha; // move was already fully determined and stored
		}
		
		int bestScore = NOSCORE;
		
		Vector<BoardAndWindow> moveList = new Vector<BoardAndWindow>();
		
		for (long likelyMoves = position.generateLikelyMoves(turn);
				likelyMoves != 0;
				likelyMoves &= (likelyMoves - 1)) {
			int movePos = BitUtil.ulog2(BitUtil.lowSetBit(likelyMoves));
			int moveX = OthelloBitBoard.xyTox(movePos);
			int moveY = OthelloBitBoard.xyToy(movePos);
			
			if (!position.moveIsLegal(moveX, moveY, turn)) {
				continue;
			}
			
			OthelloBitBoard newPosition = position.copyAndMakeMove(moveX, moveY, turn);
			
			//search the table for the most well-searched window relating to this new position
			Window tWindow = null;
			for (int i = maxSearchDepth; i >= minDepthToStore && tWindow == null; --i) {
				tWindow = transpositionTable.get(new BoardAndDepth(newPosition, i, turn ^ 1));
			}
			
			if (tWindow == null) {
				tWindow = new Window(LOWESTSCORE, HIGHESTSCORE);
			}
			
			moveList.add(new BoardAndWindow(newPosition, tWindow)); //add entry and known info to list
		}
		
		Collections.sort(moveList); // sort, placing most likely to cutoff first
		
		for (BoardAndWindow p : moveList) {
			int newScore;
			if (depth <= 1) { // base case
				newScore = evaluateLeaf(p.board, turn);
				++leafCount;
			} else {// recurse
				if (maxSearchDepth - depth >= levelsToSort) {
					if (depth - 1 < minDepthToStore) {
						newScore = -alphaBetaNoTable(p.board, -beta, -Math.max(
								alpha, bestScore), turn ^ 1, depth - 1);
					} else {
						newScore = -alphaBetaNoSort(p.board, -beta, -Math.max(
								alpha, bestScore), turn ^ 1, depth - 1);
					}
				} else  {
					newScore = -alphaBetaSorted(p.board, -beta, -Math.max(
							alpha, bestScore), turn ^ 1, depth - 1);
				}
			}

			if (newScore > bestScore) {
				bestScore = newScore;

				if (bestScore >= beta) {// prune this branch
					break;
				}
			}
		}
		
		if (bestScore == NOSCORE) { // if NO move was found... the game is over here
			if (position.canMove(turn ^ 1)) {
				// player loses turn
				if (maxSearchDepth - depth >= levelsToSort) {
					if (depth - 1 < minDepthToStore) {
						bestScore = -alphaBetaNoTable(position, -beta, -alpha, turn ^ 1, depth - 1);
					} else {
						bestScore = -alphaBetaNoSort(position, -beta, -alpha, turn ^ 1, depth - 1);
					}
				} else {
					bestScore = -alphaBetaSorted(position, -beta, -alpha, turn ^ 1, depth - 1);
				}
			} else {
				//end of game
				bestScore = evaluateEnd(position, turn);
			}
		}
		
		if (bestScore <= alpha) { // if fail low
			storedWindow.beta = bestScore; // we know that at BEST the score is this bad
		} else if (bestScore >= beta) {
			storedWindow.alpha = bestScore;
		} else {
			storedWindow.alpha = storedWindow.beta = bestScore; // store exact value
		}
		
		if (transpositionTable.size() < maxTableEntries) {
			transpositionTable.put(storedBoard, storedWindow); // store results for future lookup
		}
		
		return bestScore;
	}
	
	/**
	 * negamax search with Alpha-beta pruning, with transpositions
	 * does not sort for optimal move ordering
	 * intended for mid-level searching (not near root or leaf)
	 * 
	 * @param position : current position to analyze
	 * @param alpha : lower bound on the window
	 * @param beta : upper bound on the window
	 * @param turn : current turn (WHITE or BLACK)
	 * @param depth : how deep to recurse
	 * @return the value of the best score found
	 */
	protected int alphaBetaNoSort(OthelloBitBoard position, int alpha, int beta, 
			int turn, int depth) {
		BoardAndDepth storedBoard = new BoardAndDepth(position, depth, turn);
		Window storedWindow = transpositionTable.get(storedBoard);
		
		++nodesSearched;
		
		if (storedWindow != null)
		{
			++nodesRetrieved;
			
			//if we know that this stored position
			if (storedWindow.alpha >= beta) {
				return storedWindow.alpha;
			}
			if (storedWindow.beta <= alpha) {
				return storedWindow.beta;
			}
			
			//align windows
			alpha = Math.max(alpha, storedWindow.alpha);
			beta = Math.min(beta, storedWindow.beta);
		} else {
			storedWindow = new Window(); // go ahead and allocate
		}
		
		if (alpha == beta) {
			return alpha; // move was already fully determined and stored
		}
		
		int bestScore = NOSCORE;
		
		for (long likelyMoves = position.generateLikelyMoves(turn);
				likelyMoves != 0;
				likelyMoves &= (likelyMoves - 1)) {
			int movePos = BitUtil.ulog2(BitUtil.lowSetBit(likelyMoves));
			int moveX = OthelloBitBoard.xyTox(movePos);
			int moveY = OthelloBitBoard.xyToy(movePos);
			
			if (!position.moveIsLegal(moveX, moveY, turn)) {
				continue;
			}
			
			OthelloBitBoard newPosition = position.copyAndMakeMove(moveX, moveY, turn);
			
			int newScore;
			if (depth <= 1) { // base case
				newScore = evaluateLeaf(newPosition, turn);
				++leafCount;
			} else {//recurse
				if (depth - 1 < minDepthToStore) {
					newScore = -alphaBetaNoTable(newPosition, -beta, 
							-Math.max(alpha, bestScore), turn ^ 1, depth - 1);
				} else {
					newScore = -alphaBetaNoSort(newPosition, -beta, 
							-Math.max(alpha, bestScore), turn ^ 1, depth - 1);
				}
			}
			
			if (newScore > bestScore) {
				bestScore = newScore;
				
				if (bestScore >= beta) {// prune this branch
					break;
				}
			}	
		}
		
		if (bestScore == NOSCORE) { // if NO move was found... the game is over here
			if (position.canMove(turn ^ 1)) {
				// player loses turn
				if (depth - 1 < minDepthToStore) {
					bestScore = -alphaBetaNoTable(position, -beta, -alpha, turn ^ 1, depth - 1);
				} else {
					bestScore = -alphaBetaNoSort(position, -beta, -alpha, turn ^ 1, depth - 1);
				}
			} else {
				//end of game
				bestScore = evaluateEnd(position, turn);
			}
		}
		
		if (bestScore <= alpha) { // if fail low
			storedWindow.beta = bestScore; // we know that at BEST the score is this bad
		} else if (bestScore >= beta) {
			storedWindow.alpha = bestScore; // we know that the score is at LEAST this good
		} else {
			storedWindow.alpha = storedWindow.beta = bestScore; // store exact value
		}
		
		if (transpositionTable.size() < maxTableEntries) {
			transpositionTable.put(storedBoard, storedWindow); // store results for future lookup
		}
		
		return bestScore;
	}
	
	/**
	 * negamax search with Alpha-beta pruning, with no transpositions
	 * intended for near-leaf computations
	 * 
	 * @param position : current position to analyze
	 * @param alpha : lower bound on the window
	 * @param beta : upper bound on the window
	 * @param turn : current turn (WHITE or BLACK)
	 * @param depth : how deep to recurse
	 * @return the value of the best score found
	 */
	protected int alphaBetaNoTable(OthelloBitBoard position, int alpha, int beta, 
			int turn, int depth) {
		++nodesSearched;
		int bestScore = NOSCORE;
		
		for (long likelyMoves = position.generateLikelyMoves(turn);
				likelyMoves != 0;
				likelyMoves &= (likelyMoves - 1)) {
			int movePos = BitUtil.ulog2(BitUtil.lowSetBit(likelyMoves));
			int moveX = OthelloBitBoard.xyTox(movePos);
			int moveY = OthelloBitBoard.xyToy(movePos);
			
			if (!position.moveIsLegal(moveX, moveY, turn)) {
				continue;
			}
			
			OthelloBitBoard newPosition = position.copyAndMakeMove(moveX, moveY, turn);
			
			int newScore;
			if (depth <= 1) { // base case
				newScore = evaluateLeaf(newPosition, turn);
				++leafCount;
			} else {//recurse
				newScore = -alphaBetaNoTable(newPosition, -beta, 
						-Math.max(alpha, bestScore), turn ^ 1, depth - 1);
			}
			
			if (newScore > bestScore) {
				bestScore = newScore;
				
				if (bestScore >= beta) {
					return bestScore; // prune this branch
				}
			}	
		}
		
		if (bestScore == NOSCORE) { // if NO move was found...
			if (position.canMove(turn ^ 1)) {
				// player loses turn
				bestScore = -alphaBetaNoTable(position, -beta, -alpha, turn ^ 1, depth - 1);
			} else {
				//end of game
				bestScore = evaluateEnd(position, turn);
			}
		}
		
		return bestScore;
	}
	
	/**
	 * estimates the value of the position for use in leaf nodes
	 * 
	 * @param position : current position
	 * @param turn: who's turn (WHITE or BLACK)
	 * @return an estimation of the 'quality' of this positon
	 */
	public static int evaluateLeaf(OthelloBitBoard position, int turn) {
		return evaluateStateForPlayer(position, turn) - evaluateStateForPlayer(position, turn ^ 1);
	}
	
	/**
	 * internal function that guages the quality of the board with respect to a player
	 * 
	 * @param position
	 * @param state
	 * @return quality for a certain player
	 */
	private static int evaluateStateForPlayer(OthelloBitBoard position, int state) {
		int pieceScore = position.countPieces(state);
		int positionScore = 0;
		
		long cBoard = (state == WHITE) ? position.white : position.black;
		
		positionScore += 2*BitUtil.countSetBits(cBoard & 0x8100000000000081L); // corners
		positionScore += BitUtil.countSetBits(cBoard & 0xFF818181818181FFL); // edge
		
		return pieceScore + positionScore;
	}
	
	/**
	 * evaluate the results of a game that has ended
	 * 
	 * @param position
	 * @param state
	 * @return score for an ended game
	 */	
	protected int evaluateEnd(OthelloBitBoard position, int state) {
		int pieceDiff = position.countPieces(state) - position.countPieces(state ^ 1);
		
		if (pieceDiff < 0) {
			return LOWESTSCORE + 1 + position.countPieces(state); // LOSE
		} else if (pieceDiff == 0) {
			return valueOfDraw;
		} else {
			return HIGHESTSCORE - 1 - position.countPieces(state ^ 1); //win
		}
	}
	
	/**
	 * Performs a narrow-window search about a known score and returns the move instead
	 * of the score.
	 * 
	 * @return the best move (0-63). Use xyTox() and xyToy() to
	 *  extract x and y values. Returns -1 in the event of an error.
	 */
	public int retreiveBestMove() {
		if (scoreOfConfiguration == NOSCORE) {
			return -1;
		}
		
		int bestScore = NOSCORE;
		int bestMove = -1;
		int alpha = (scoreOfConfiguration > LOWESTSCORE) ? scoreOfConfiguration - 1 : LOWESTSCORE;
		int beta = (scoreOfConfiguration < HIGHESTSCORE) ? scoreOfConfiguration + 1 : HIGHESTSCORE;
		
		for (long likelyMoves = rootNode.generateLikelyMoves(rootNodeTurn);
				likelyMoves != 0;
				likelyMoves &= (likelyMoves - 1)) {
			int movePos = BitUtil.ulog2(BitUtil.lowSetBit(likelyMoves));
			int moveX = OthelloBitBoard.xyTox(movePos);
			int moveY = OthelloBitBoard.xyToy(movePos);
			
			if (!rootNode.moveIsLegal(moveX, moveY, rootNodeTurn)) {
				continue;
			}
			
			OthelloBitBoard newPosition = rootNode.copyAndMakeMove(moveX, moveY, rootNodeTurn);
			
			int newScore;
			if (maxSearchDepth <= 1) { // base case
				newScore = evaluateLeaf(newPosition, rootNodeTurn);
				++leafCount;
			} else {//recurse
				newScore = -alphaBetaSorted(newPosition, -beta, 
						-Math.max(alpha, bestScore), rootNodeTurn ^ 1, maxSearchDepth - 1);
			}
			
			if (newScore > bestScore) {
				bestScore = newScore;
				bestMove = movePos;
				
				if (bestScore >= beta) {
					System.err.println("Error: failed to retreive move." + 
							"Score was incorrect. Real score was >= " + beta);
					return -1;
				}
				
				if (newScore == scoreOfConfiguration) {
					break; // we've found a sufficient score
				}
			}	
		}
		
		if (bestScore == NOSCORE) { // if NO move was found...
			System.err.println("Warning... player cannot move. AI should not have been executed.");
			return -1;
		}
		
		return bestMove;
	}
	
	/**
	 * extracts x coordinate from an 'xy' int
	 * 
	 * @param xy : position (0-63)
	 * @return extracted x coordinate
	 */
	public static int xyTox(int xy) {
		return OthelloBitBoard.xyTox(xy);
	}
	
	/**
	 * extracts y coordinate from an 'xy' int
	 * 
	 * @param xy : position (0-63)
	 * @return extracted y coordinate
	 */
	public static int xyToy(int xy) {
		return OthelloBitBoard.xyToy(xy);
	}

	public int getLeafCount() {
		return leafCount;
	}

	public int getNodesSearched() {
		return nodesSearched;
	}
	
	public int getNodesRetreived() {
		return nodesRetrieved;
	}
	
	public void resetCounters() {
		leafCount = 0;
		nodesSearched = 0;
		nodesRetrieved = 0;
	}
	
	public int getMinDepthToStore() {
		return minDepthToStore;
	}

	public void setMinDepthToStore(int minDepthToStore) {
		this.minDepthToStore = minDepthToStore;
	}

	public int getValueOfDraw() {
		return valueOfDraw;
	}

	public void setValueOfDraw(int valueOfDraw) {
		this.valueOfDraw = valueOfDraw;
	}

	public int getMaxSearchDepth() {
		return maxSearchDepth;
	}

	public void setMaxSearchDepth(int maxSearchDepth) {
		this.maxSearchDepth = maxSearchDepth;
		scoreOfConfiguration = 0; //reset, because this score will no longer be valid.
	}

	public int getLevelsToSort() {
		return levelsToSort;
	}

	public void setLevelsToSort(int levelsToSort) {
		this.levelsToSort = levelsToSort;
	}
	
	public void setRootNode(OthelloBoard board, int turn) {
		rootNode = new OthelloBitBoard(board);
		rootNodeTurn = turn;
		scoreOfConfiguration = NOSCORE;
	}

	/**
	 * @param args
	 * 
	 * run alpha-beta tests
	 */
	public static void main(String[] args) {
		long begin = System.currentTimeMillis();

		System.out.println("Alpha-Beta search");
		OthelloBitBoard test1 = new OthelloBitBoard(0x0000002C14000000L, 0x0000381028040000L);
		//OthelloBitBoard test1 = new OthelloBitBoard(0xFFBFCD5D4D0F07D9L, 0x004002829210C824L);
		
		OthelloAlphaBeta testObj = new OthelloAlphaBeta();
		testObj.setMaxSearchDepth(10);
		testObj.setLevelsToSort(3);
		testObj.setRootNode(test1, WHITE);

		System.out.println("score: " + testObj.alphaBetaSearch(LOWESTSCORE, HIGHESTSCORE));
		
		long r2 = System.currentTimeMillis();
		System.out.println("best move " + testObj.retreiveBestMove());
		
		
		System.out.println("leaf nodes: " + testObj.getLeafCount());
		System.out.println("non-leaf nodes: " + testObj.getNodesSearched());
		System.out.println("nodes retreived: " + testObj.getNodesRetreived());
		System.out.println("table size: " + testObj.transpositionTable.size());
		
		System.out.println("time: " + (System.currentTimeMillis() - begin));
		System.out.println("re-search time: " + (System.currentTimeMillis() - r2));
	}
}