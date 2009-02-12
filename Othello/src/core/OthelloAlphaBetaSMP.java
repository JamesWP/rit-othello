/**
 *
 */
package core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;

import edu.rit.pj.ParallelRegion;
import edu.rit.pj.ParallelTeam;

/**
 *
 * @author Nicholas Ver Hoeve
 */
public class OthelloAlphaBetaSMP extends OthelloAlphaBeta {
	Queue<JobRequest> jobQueue;

	int sharedSearchDepth = 1;
	int localTableSize;

	int totalJobsExecuted;
	int leafJobsExecuted;
	int jobsSkipped;

	JobRequest rootJob = null;

	List<OthelloAlphaBeta> localSearches;
	List<Queue<JobRequest>> localJobs;

	Random rand = new Random();

	OthelloAlphaBetaSMP(int localTableSize) {
		this.localTableSize = localTableSize;
		jobQueue = new ArrayBlockingQueue<JobRequest>(100, true);
	}

	OthelloAlphaBetaSMP() {
		localTableSize = 250000;
		jobQueue = new ArrayBlockingQueue<JobRequest>(100, true);
	}

	protected AlphaBetaJobRequest enqueueAlphaBetaSMP(int alpha, int beta) {
		AlphaBetaJobRequest job = new AlphaBetaJobRequest(rootNode,
				maxSearchDepth,
				rootNodeTurn,
				new Window(alpha, beta));
		jobQueue.add(job);
		return job;
	}

	protected class JobRequest {
		JobRequest parentJob;
		List<JobRequest> childJobs;

		boolean started = false;
		boolean complete = false;
		boolean cancelled = false;

		public void spawnChildJobs() {}
		public void childCompletionUpdate(JobRequest child) {}
		public void onExecute(int threadIndex) {}
		public void executeJob(int threadIndex) {
			started = true;
			if (!cancelled && !complete) {
				onExecute(threadIndex);
				complete = (childJobs == null) || childJobs.isEmpty();
			}
		}
		public void updateChildWindow(Window window) {}

		protected void cancelAllChildJobs() {
			if (childJobs != null) {
				for (JobRequest j : childJobs) {
					j.cancelled = true;
					if (!(j.cancelled || j.complete)) {
						j.cancelAllChildJobs();
					}
				}

				childJobs.clear();
			}
		}
	};

	protected class AlphaBetaJobRequest extends JobRequest {
		BoardAndDepth item;
		Window searchWindow;

		int bestScore;

		public AlphaBetaJobRequest(OthelloBitBoard position, int depth, int turn, Window window) {
			item = new BoardAndDepth(position, depth, turn);
			searchWindow = new Window(window);
			parentJob = null;
			init();
		}

		protected AlphaBetaJobRequest(AlphaBetaJobRequest parent, OthelloBitBoard position) {
			parentJob = parent;
			item = new BoardAndDepth(position,
					parent.item.getDepth() - 1,
					parent.item.getTurn() ^ 1);

			searchWindow = new Window();
			parent.updateChildWindow(searchWindow);
			init();
		}

		protected AlphaBetaJobRequest(JobRequest parent, BoardAndDepth item, Window window) {
			parentJob = parent;
			this.item = item;
			searchWindow = window;
			parent.updateChildWindow(searchWindow);
			init();
		}

		private void init() {
			bestScore = NOSCORE;
			checkJobNecessity();
		}

		public boolean checkJobNecessity() {

			// Look up the chain to see if this is no longer needed
			// For instance a parent is completed or canceled
			for (JobRequest j = parentJob;
				j != null;
				j = j.parentJob) {
				if ( j.cancelled || j.complete ) {
					return false;
				}
			}

			Window storedWindow = transpositionTable.get(item);

			if (parentJob != null) {
				parentJob.updateChildWindow(searchWindow);
			}

			++nodesSearched;

			if (storedWindow != null)
			{
				++nodesRetrieved;

				//check if we already know the result to be outside of what we care about
				if (storedWindow.alpha >= searchWindow.beta) {
					reportJobComplete(storedWindow.alpha);
				}
				if (storedWindow.beta <= searchWindow.alpha) {
					reportJobComplete(storedWindow.beta);
				}

				//align windows
				searchWindow.alpha = Math.max(searchWindow.alpha, storedWindow.alpha);
				searchWindow.beta = Math.min(searchWindow.beta, storedWindow.beta);
			}

			if (searchWindow.alpha == searchWindow.beta) {
				reportJobComplete(searchWindow.alpha); // result is already known
			}

			return !complete;
		}

		public synchronized void childCompletionUpdate(JobRequest child) {
			if (complete) {
				System.out.println("Warning: Parent was was already complete...");
				return;
			}

			if (child instanceof AlphaBetaJobRequest) {
				AlphaBetaJobRequest childNode = (AlphaBetaJobRequest)child;

				//negamax scoring
				bestScore = Math.max(bestScore, -childNode.bestScore);

				childJobs.remove(child);
				if (bestScore >= searchWindow.beta || /*beta cutoff check*/
						childJobs.isEmpty() /*moves exhausted check*/) {
					reportJobComplete(bestScore);
				}
			}
		}

		public synchronized void reportJobComplete(int score) {
			bestScore = score;
			cancelAllChildJobs();

			Window storedWindow = transpositionTable.get(item);
			if (storedWindow == null) {
				storedWindow = new Window();
			}

			if (score <= searchWindow.alpha) { // if fail low
				storedWindow.beta = score; // we know that at BEST the score is this bad
			} else if (score >= searchWindow.beta) {
				storedWindow.alpha = score; // we know that the score is at LEAST this good
			} else {
				storedWindow.alpha = storedWindow.beta = score; // store exact value
			}

			if (transpositionTable.size() < maxTableEntries) {
				transpositionTable.put(item, storedWindow); // store results for future lookup
			}

			if (cancelled) {
				System.out.println("Job completed after cancellation. Wasted time.");
			} else {
				if (parentJob == null) { //root job is finishing

				} else {
					parentJob.childCompletionUpdate(this);
				}
			}

			complete = true;
		}

		public void spawnChildJobs(int threadIndex) {
			if (cancelled || complete || childJobs != null) {
				System.out.println("cancelled: " + cancelled + "  complete: " + complete);
				return;
			}

			int turn = item.getTurn();
			childJobs = Collections.synchronizedList(new Vector<JobRequest>(16));

			Vector<BoardAndWindow> moveList = new Vector<BoardAndWindow>();

			for (long likelyMoves = item.generateLikelyMoves(turn);
					likelyMoves != 0;
					likelyMoves &= (likelyMoves - 1)) {
				int movePos = BitUtil.ulog2(BitUtil.lowSetBit(likelyMoves));
				int moveX = OthelloBitBoard.xyTox(movePos);
				int moveY = OthelloBitBoard.xyToy(movePos);

				if (!item.moveIsLegal(moveX, moveY, turn)) {
					continue;
				}

				OthelloBitBoard newPosition = item.copyAndMakeMove(moveX, moveY, turn);

				//search the table for the most well-searched window relating to this new position
				Window tWindow = null;
				for (int i = maxSearchDepth;
					i >= (maxSearchDepth - item.getDepth()) && tWindow == null;
					--i) {
					tWindow = transpositionTable.get(new BoardAndDepth(newPosition, i, turn ^ 1));
				}

				if (tWindow == null) {
					tWindow = new Window(LOWESTSCORE, HIGHESTSCORE);
				}

				moveList.add(new BoardAndWindow(newPosition, tWindow)); //add entry and known info to list
			}

			if (moveList.isEmpty()) { // if NO move was found...
				if (item.canMove(turn ^ 1)) {
					// player loses turn
					enqueueChildJob(item, threadIndex);
				} else {
					//end of game
					reportJobComplete(evaluateEnd(item, turn));
				}
			} else {
				Collections.sort(moveList); // sort, placing most likely to cutoff first

				for (BoardAndWindow p : moveList) {
					//request all child jobs in sorted order
					enqueueChildJob(p.board, threadIndex);
				}
			}
		}

		private void enqueueChildJob(OthelloBitBoard newPosition, int threadIndex) {
			JobRequest s = new AlphaBetaJobRequest(this, newPosition);
			rootJob = s;
			childJobs.add(s);
			enqueueJob(s, threadIndex);
		}

		public void onExecute(int threadIndex) {
			if (checkJobNecessity()) {
				if ((maxSearchDepth - item.getDepth()) >= sharedSearchDepth) {
					OthelloAlphaBeta localSearch = localSearches.get(threadIndex);
					localSearch.setMaxSearchDepth(maxSearchDepth - sharedSearchDepth);
					localSearch.setLevelsToSort(levelsToSort - sharedSearchDepth);
					localSearch.setValueOfDraw(valueOfDraw);
					localSearch.setRootNode(item, item.getTurn());
					localSearch.setMinDepthToStore(3);

					//bulk of slowness that is meant to run in parallel
					int score = localSearch.alphaBetaSearch(searchWindow.alpha, searchWindow.beta);
					System.out.println("Window [" + searchWindow.alpha + ", " + searchWindow.beta + "] = " + score);
					System.out.println("leaves:" + getLeafCount());

					//stats tracking (maybe switch off for parallel performance)
					leafCount += localSearch.getLeafCount();
					nodesSearched += localSearch.getNodesSearched();

					++leafJobsExecuted;

					reportJobComplete(score);
				} else {
					spawnChildJobs(threadIndex);
				}
			}
		}

		public void updateChildWindow(Window window) {
			window.alpha = Math.max(-searchWindow.beta, window.alpha);
			window.beta = Math.min(-Math.max(bestScore, searchWindow.alpha), window.beta);
		}

		public int getBestScore() {
			return bestScore;
		}
	}

	public int getTotalJobsExecuted() {
		return totalJobsExecuted;
	}

	public int getLeafJobsExecuted() {
		return leafJobsExecuted;
	}

	public int getJobsSkipped() {
		return jobsSkipped;
	}

	public int getSharedSearchDepth() {
		return sharedSearchDepth;
	}

	public void setSharedSearchDepth(int sharedDepth) {
		sharedSearchDepth = sharedDepth;
	}

	public void resetCounters() {
		super.resetCounters();
		leafJobsExecuted = 0;
		totalJobsExecuted = 0;
	}

	private void enqueueJob(JobRequest job, int threadIndex) {
		if (threadIndex == -1) {
			jobQueue.add(job);
		} else {
			localJobs.get(threadIndex).add(job);
		}
	}

	private void prepareLocalSearches(int m) {
		if (localSearches == null) {
			localSearches = new Vector<OthelloAlphaBeta>(1);
		}

		for (int i = localSearches.size(); i < m; ++i) {
			localSearches.add(new OthelloAlphaBeta(localTableSize));
		}
	}

	private void prepareLocalJobQueues(int m) {
		localJobs = Collections.synchronizedList(new ArrayList<Queue<JobRequest>>(m));

		for (int i = 0; i < m; ++i) {
			localJobs.add(new ArrayBlockingQueue<JobRequest>(2000));
		}

		//randomize jobs into local queues
		while(!jobQueue.isEmpty()) {
			JobRequest j = jobQueue.poll();

			if (j != null) {
				int index = Math.abs(rand.nextInt()) % m;
				localJobs.get(index).add(j);
			}
		}
	}

	private synchronized JobRequest pullJob(List<Queue<JobRequest>> localList, int index) {
		JobRequest j = localList.get(index).poll();
		if (j != null) {
			return j;
		}

		//else steal a job
		int size = localList.size();
		int end = Math.abs( rand.nextInt() ) % size;
		int start = (end + 1) % size;
		for (int i = start; i != end; i = ((i+1)%size)) {
			j = localList.get(i).poll();

			if (j != null) {
				return j;
			}
		}

		return null;
	}

	/**
	 * Execution of the job queue
	 */
	public void executeJobQueue(int threadIndex) {
		while (!(rootJob.complete || rootJob.cancelled)) {
			JobRequest j = jobQueue.poll();

			if (j != null) {
				j.executeJob(threadIndex);
				++totalJobsExecuted;
			}
		}
	}

	/**
	 * Parallel execution of the job queue
	 */
	public void parallelExecution(int threads) {
		// Manually adjust the number of threads for ease.
		try {
			prepareLocalSearches(threads);
			prepareLocalJobQueues(threads);

			new ParallelTeam(threads).execute(new ParallelRegion() {
				public void run() throws Exception {
					System.out.println( getThreadIndex() + " started" );
					List<Queue<JobRequest>> localList = new ArrayList<Queue<JobRequest>>(localJobs);

					while (!(rootJob.complete || rootJob.cancelled)) {
						JobRequest j = pullJob(localList, getThreadIndex());

						if (j != null) {
							j.executeJob(getThreadIndex());
							++totalJobsExecuted;
						}
					}
					System.out.println( getThreadIndex() + " says its done");
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Prime the queue by processing one job to create more jobs
	 */
	public void jumpStart(int n) {
		prepareLocalSearches(1);

		for (int i = 0; i < n && !jobQueue.isEmpty(); ++i) {
			jobQueue.poll().executeJob(-1);
			++totalJobsExecuted;
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		long begin = System.currentTimeMillis();

		System.out.println("Alpha-Beta search");

		OthelloBitBoard test1 = new OthelloBitBoard(0x0000002C14000000L, 0x0000381028040000L);

		OthelloAlphaBetaSMP testObj = new OthelloAlphaBetaSMP();
		testObj.setMaxSearchDepth(10);
		testObj.setLevelsToSort(4);
		testObj.setRootNode(test1, WHITE);

		AlphaBetaJobRequest job = testObj.enqueueAlphaBetaSMP(LOWESTSCORE, HIGHESTSCORE);

		// Jump Start
		System.out.println("Before Jump Start");
		testObj.jumpStart(1);
		System.out.println("After Jump Start");

		testObj.parallelExecution(2);

		System.out.println("score: " + job.bestScore);

		System.out.println("leaf nodes: " + testObj.getLeafCount());
		System.out.println("non-leaf nodes: " + testObj.getNodesSearched());
		System.out.println("nodes retreived: " + testObj.getNodesRetreived());
		System.out.println("table size: " + testObj.transpositionTable.size());

		System.out.println("totalJobsExecuted: " + testObj.getTotalJobsExecuted());
		System.out.println("leafJobsExecuted: " + testObj.getLeafJobsExecuted());
		System.out.println("jobsSkipped: " + testObj.getJobsSkipped());

		System.out.println("time: " + (System.currentTimeMillis() - begin));
	}
}