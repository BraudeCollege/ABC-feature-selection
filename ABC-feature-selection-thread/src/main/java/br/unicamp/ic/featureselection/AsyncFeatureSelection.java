package br.unicamp.ic.featureselection;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import weka.classifiers.Classifier;
import weka.classifiers.lazy.IBk;
import weka.core.Instances;
import weka.filters.Filter;
import br.unicamp.ic.classifier.ClassifierExecutor;
import br.unicamp.ic.classifier.KFoldClassifierExecutor;
import br.unicamp.ic.featureselection.swarm.FoodSource;
import br.unicamp.ic.util.FileUtil;

/**
 * Respons�vel por fazer a sele��o de features
 * 
 * @author Mauricio Schiezaro
 */
public class AsyncFeatureSelection {

	private BufferedWriter writer;

	/**
	 * Estrat�gia de execu��o do classificador
	 */
	private static final int KFOLD = 10;

	/**
	 * Quantidade m�ximo poss�vel de features
	 */
	private int featureSize;

	/**
	 * N�mero m�ximo de vezes que visitaremos uma fonte de alimento antes de
	 * abandon�-la
	 */
	private int limit;

	/**
	 * N�mero de execu��es do algoritmo ABC
	 */
	private int runtime;

	/**
	 * Fitness da melhor fonte de alimento
	 */
	private double bestFitness;

	/**
	 * Melhor fonte de alimento
	 */
	private FoodSource bestFoodSource;

	/**
	 * Par�metro que controlor� a perturba��o de quantas features ser�o
	 * modificadas ao explorar a vizinhan�a
	 */
	private double mr;

	/**
	 * Conjunto de fontes de alimento
	 */
	private Set<FoodSource> foodSources;

	/**
	 * Fontes de alimento que j� foram visitadas
	 */
	private Set<FoodSource> visitedFoodSources;

	/**
	 * Armazena os Scout Bees e para posteriormente inclu�-los como fonte de
	 * alimento
	 */
	private Set<FoodSource> scouts;

	/**
	 * Armazena as fontes abandonadas para posteriormente remov�-las como fonte
	 * de alimento
	 */
	private Set<FoodSource> abandoned;

	/**
	 * Indica se na explora��o modificaremos apenas uma feature por vez
	 * {@link PerturbationStrategy.CHANGE_ONE_FEATURE} ou atrav�s do par�metro
	 * de perturba��o MR {@link PerturbationStrategy.USE_MR}
	 */
	private PerturbationStrategy perturbation;

	/**
	 * Guarda quantas fontes de alimento o algoritmo consultou durante a busca
	 */
	private long states;

	private FileUtil fileUtil;

	private Classifier knnClassifier = new IBk();

	private Instances originalInstances;
	
	private static final int THREAD_NUMBER = 8;

	/**
	 * Construtor para sele��o de features com a fontes as fontes de alimento
	 * sendo inicializadas uma por feature e utilizando o par�metro de
	 * perturba��o MR que define quantos par�metros s�o moficados ao explorar a
	 * vizinhan�a
	 * 
	 * @param runtime
	 *            N�mero de execu��es do ABC
	 * @param limit
	 *            M�ximo n�mero de vezes que ser� explorado uma vizinhan�a,
	 *            antes de abandonar a fonte de alimento
	 * @param mr
	 *            Par�metro de perturba��o para definir quantas features ser�o
	 *            inclu�das ou exclu�das durante a explora��o. Se mr > 0
	 *            perturbation = PerturbationStrategy.USE_MR, se mr <=0
	 *            perturbation = PerturbationStrategy.CHANGE_ONE_FEATURE;
	 * @param executor
	 *            Respons�vel pela classifica��o
	 * 
	 */
	public AsyncFeatureSelection(int runtime, int limit, double mr,
			String databaseName, Filter... filter) {
		this.runtime = runtime;
		this.limit = limit;
		this.mr = mr;
		if (mr > 0) {
			perturbation = PerturbationStrategy.USE_MR;
		} else {
			perturbation = PerturbationStrategy.CHANGE_ONE_FEATURE;
		}
		try {
			// carrega os dados do arquivo
			originalInstances = new Instances(new FileReader(
					System.getProperty("user.dir") + "/src/main/resources/"
							+ databaseName));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		if (filter != null) {
			for (int i = 0; i < filter.length; i++) {

				try {
					filter[i].setInputFormat(originalInstances);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				try {
					originalInstances = Filter.useFilter(originalInstances,
							filter[i]);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}

			}
		}
		this.featureSize = originalInstances.numAttributes() - 1;
	}

	/**
	 * Construtor para sele��o de features com a fontes as fontes de alimento
	 * sendo inicializadas uma por feature e alterando apenas uma feature por
	 * vez ao explorar a vizinhan�a
	 * 
	 * @param runtime
	 *            N�mero de execu��es do ABC
	 * @param limit
	 *            M�ximo n�mero de vezes que ser� explorado uma vizinhan�a,
	 *            antes de abandonar a fonte de alimento
	 * @param executor
	 *            Respons�vel pela classifica��o
	 */
	public AsyncFeatureSelection(int runtime, int limit, String databaseName,
			Filter... filter) {
		this(runtime, limit, 0, databaseName, filter);
	}

	/**
	 * Executa todo o processo de sele��o
	 */
	public void execute() {
		visitedFoodSources = new ConcurrentSkipListSet<FoodSource>();
		abandoned = new ConcurrentSkipListSet<FoodSource>();

		fileUtil = FileUtil.newInstance();
		writer = fileUtil.getWriter();

		bestFitness = 0;

		states = 0;

		logFeatureSeletionInit(runtime, limit, mr, perturbation, featureSize);
		long time = System.currentTimeMillis();
		
		initializeFoodSources();
		System.out.println((System.currentTimeMillis() - time) / 60000);
		for (int i = 0; i < runtime; i++) {
			scouts = new ConcurrentSkipListSet<FoodSource>();
			sendEmployedBees();
			fileUtil.flush();
			sendOnlookerBees();
			fileUtil.flush();
			sendScoutBeesAndRemoveAbandonsFoodSource();
			fileUtil.flush();
			logBestSolutionEachIteration(i);
			fileUtil.flush();
		}
		time = (System.currentTimeMillis() - time) / 60000;
		logBestSolutionAndExecutionTime(time);
		states = 0;
	}

	/**
	 * Inicializa as fontes de alimento com o mesmo n�mero de features, cada
	 * fonte tem selecionado apenas uma feature, a medida que o algoritmo �
	 * executado novas features s�o adicionadas ao conjunto
	 */
	private void initializeFoodSources() {

		System.out.println("initializeFoodSources");

		foodSources = new ConcurrentSkipListSet<FoodSource>();

		ExecutorService executor = Executors.newFixedThreadPool(THREAD_NUMBER);
		CompletionService<FoodSource> completionService = new ExecutorCompletionService<FoodSource>(
				executor);
		
		int loopSize = featureSize / THREAD_NUMBER;

		int rest = featureSize % THREAD_NUMBER;
		
		int index = 0;

		for (int i = 0; i < loopSize; i++) {
			for (int j = 0; j < THREAD_NUMBER; j++)  {
				states++;
				boolean features[] = new boolean[featureSize];
				features[index++] = true;
				// calcula a qualidade do conjunto criado paralelizando em
				// THREAD_NUMBER threads
				System.out.print(index + " ");
				ClassifierExecutionCallable callable = new ClassifierExecutionCallable(
						features);
				completionService.submit(callable);
			}
			System.out.println("Get ");
			for (int j = 0; j < THREAD_NUMBER; j++) {
				// recupera os valores conforme s�o retornados da thread
				FoodSource foodSource = null;
				try {
					Future<FoodSource> foodsFuture = completionService.take();
					System.out.print("Take ");
					foodSource = foodsFuture.get();
					System.out.print(j + " ");
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				} catch (ExecutionException e) {
					throw new RuntimeException(e);
				}
				// calcula a qualidade do conjunto criado
				double curFitness = foodSource.getFitness();
				// adicionamos fonte de alimento
				foodSources.add(foodSource);
				// armazena a melhor
				if (curFitness > bestFitness) {
					bestFoodSource = new FoodSource(foodSource);
					bestFitness = curFitness;
				}
			}
		}
		for (int i = 0; i < rest; i++) {
			states++;
			boolean features[] = new boolean[featureSize];
			features[index++] = true;
			// calcula a qualidade do conjunto criado paralelizando em
			// THREAD_NUMBER threads

			System.out.print(index + " ");
			ClassifierExecutionCallable callable = new ClassifierExecutionCallable(
					features);

			completionService.submit(callable);
		}
		System.out.println("Get ");
		for (int i = 0; i < rest; i++) {
			// recupera os valores conforme s�o retornados da thread
			FoodSource foodSource = null;
			try {
				Future<FoodSource> foodsFuture = completionService.take();
				System.out.print("Take ");
				foodSource = foodsFuture.get();
				System.out.print(i + " ");
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			} catch (ExecutionException e) {
				throw new RuntimeException(e);
			}
			// calcula a qualidade do conjunto criado
			double curFitness = foodSource.getFitness();
			// adicionamos fonte de alimento
			foodSources.add(foodSource);
			// armazena a melhor
			if (curFitness > bestFitness) {
				bestFoodSource = new FoodSource(foodSource);
				bestFitness = curFitness;
			}
		}
		executor.shutdown();
	}

	/**
	 * Envia as abelhas para explorar as fontes de alimento e sua vizinhan�a
	 */
	private void sendEmployedBees() {

		System.out.println("sendEmployedBees");

		int foodSourceListSize = foodSources.size();

		if (foodSourceListSize != 0) {

			FoodSource[] sources = foodSources.toArray(new FoodSource[0]);
			
			ExecutorService executor = Executors.newFixedThreadPool(THREAD_NUMBER);
			CompletionService<BeeParallelExecutionResult> completionService = new ExecutorCompletionService<BeeParallelExecutionResult>(
					executor);
			
			int loopSize = foodSourceListSize / THREAD_NUMBER;

			int rest = foodSourceListSize % THREAD_NUMBER;
			
			int index = 0;
			
			for (int i = 0; i < loopSize; i++) {
				for (int j = 0; j < THREAD_NUMBER; j++) {
					System.out.print(index + " ");
					SendBeeCallable callable = new SendBeeCallable(sources[index++]);
					completionService.submit(callable);
				}
				for (int j = 0; j < THREAD_NUMBER; j++) {
					try {
						BeeParallelExecutionResult result = completionService
								.take().get();
						foodSources.removeAll(result.getMarkedToRemoved());
						foodSources.addAll(result.getNeighbors());
						abandoned.addAll(result.getAbandoned());
						int abandonedSize = result.getAbandoned().size();
						for (int l = 0; l < abandonedSize; l++) {
							createScoutBee();
						}
						visitedFoodSources.addAll(result.getVisitedFoodSources());
						if (result.getBestFitness() > bestFitness
								|| (result.getBestFitness() == bestFitness && result
										.getBestFoodSource().getNrFeatures() < bestFoodSource
										.getNrFeatures())) {

							bestFoodSource = new FoodSource(
									result.getBestFoodSource());
							bestFitness = result.getBestFitness();
						}
					} catch (InterruptedException e) {
						throw new RuntimeException(e);
					} catch (ExecutionException e) {
						throw new RuntimeException(e);
					}
				}
			}
			for (int i = 0; i < rest; i++) {
				System.out.print(index + " ");
				SendBeeCallable callable = new SendBeeCallable(sources[index++]);
				completionService.submit(callable);
			}
			for (int i = 0; i < rest; i++) {
				try {
					BeeParallelExecutionResult result = completionService
							.take().get();
					foodSources.removeAll(result.getMarkedToRemoved());
					foodSources.addAll(result.getNeighbors());
					abandoned.addAll(result.getAbandoned());
					int abandonedSize = result.getAbandoned().size();
					for (int l = 0; l < abandonedSize; l++) {
						createScoutBee();
					}
					visitedFoodSources.addAll(result.getVisitedFoodSources());
					if (result.getBestFitness() > bestFitness
							|| (result.getBestFitness() == bestFitness && result
									.getBestFoodSource().getNrFeatures() < bestFoodSource
									.getNrFeatures())) {

						bestFoodSource = new FoodSource(
								result.getBestFoodSource());
						bestFitness = result.getBestFitness();
					}
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				} catch (ExecutionException e) {
					throw new RuntimeException(e);
				}
			}
			executor.shutdown();
		}
	}

	private void sendOnlookerBees() {

		System.out.println("sendOnlookerBees");

		int foodSourceListSize = foodSources.size();

		if (foodSourceListSize != 0) {

			Double min = Collections.min(foodSources).getFitness();
			Double max = Collections.max(foodSources).getFitness();
			Double range = max - min;

			Random random = new Random();

			ExecutorService executor = Executors.newFixedThreadPool(THREAD_NUMBER);
			CompletionService<BeeParallelExecutionResult> completionService = new ExecutorCompletionService<BeeParallelExecutionResult>(
					executor);
			
			int index = 0;

			int loopSize = foodSourceListSize / THREAD_NUMBER;

			int rest = foodSourceListSize % THREAD_NUMBER;
			
			FoodSource[] sources = foodSources.toArray(new FoodSource[0]);

			int threadSize = 0;
			
			for (int i = 0; i < loopSize; i++) {
				for (int j = 0; j < THREAD_NUMBER; j++) {
					Double prob = (sources[index].getFitness() - min) / range;
					if (random.nextDouble() < prob) {
						System.out.print(threadSize + " ");
						SendBeeCallable callable = new SendBeeCallable(sources[index]);
						completionService.submit(callable);
						threadSize++;
					} else {
						sources[index].incrementLimit();
					}
					index++;
				}
				for (int j = 0; j < THREAD_NUMBER; j++) {
					try {
						BeeParallelExecutionResult result = completionService
								.take().get();
						foodSources.removeAll(result.getMarkedToRemoved());
						foodSources.addAll(result.getNeighbors());
						abandoned.addAll(result.getAbandoned());
						int abandonedSize = result.getAbandoned().size();
						for (int l = 0; l < abandonedSize; l++) {
							createScoutBee();
						}
						visitedFoodSources.addAll(result.getVisitedFoodSources());
						if (result.getBestFitness() > bestFitness
								|| (result.getBestFitness() == bestFitness && result
										.getBestFoodSource().getNrFeatures() < bestFoodSource
										.getNrFeatures())) {

							bestFoodSource = new FoodSource(
									result.getBestFoodSource());
							bestFitness = result.getBestFitness();
						}
					} catch (InterruptedException e) {
						throw new RuntimeException(e);
					} catch (ExecutionException e) {
						throw new RuntimeException(e);
					}
				}
			}
			for (int i = 0; i < rest; i++) {
				Double prob = (sources[index].getFitness() - min) / range;
				if (random.nextDouble() < prob) {
					System.out.print(threadSize + " ");
					SendBeeCallable callable = new SendBeeCallable(sources[index]);
					completionService.submit(callable);
					threadSize++;
				} else {
					sources[index].incrementLimit();
				}
				index++;
			}
			for (int i = 0; i < rest; i++) {
				try {
					BeeParallelExecutionResult result = completionService
							.take().get();
					foodSources.removeAll(result.getMarkedToRemoved());
					foodSources.addAll(result.getNeighbors());
					abandoned.addAll(result.getAbandoned());
					int abandonedSize = result.getAbandoned().size();
					for (int l = 0; l < abandonedSize; l++) {
						createScoutBee();
					}
					visitedFoodSources.addAll(result.getVisitedFoodSources());
					if (result.getBestFitness() > bestFitness
							|| (result.getBestFitness() == bestFitness && result
									.getBestFoodSource().getNrFeatures() < bestFoodSource
									.getNrFeatures())) {

						bestFoodSource = new FoodSource(
								result.getBestFoodSource());
						bestFitness = result.getBestFitness();
					}
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				} catch (ExecutionException e) {
					throw new RuntimeException(e);
				}
			}
			executor.shutdown();
		}

	}

	/**
	 * Verifica quais features ser�o utilizadas, eplora a vizinhan�a, criando
	 * uma varia��o dessa mesma fonte de alimento, verifica se a fonte pode ser
	 * abandonada (caso seja abandonada � necess�rio marcar para ser removida e
	 * criar um scout bee para substituir a fonte abandonada) e verifica se a
	 * fonte explorada � melhor que a atual e passa a considera-la para futuras
	 * explora��es sempre armazenando a melhor fonte de alimento
	 * 
	 * @param foodSource
	 *            Fonte de alimento a partir da qual a vizinhan�a ser� explorada
	 */
	private BeeParallelExecutionResult sendBee(FoodSource foodSource) {

		Set<FoodSource> markedToRemoved = new HashSet<FoodSource>();
		Set<FoodSource> neighbors = new HashSet<FoodSource>();
		Set<FoodSource> abandoned = new HashSet<FoodSource>();
		Set<FoodSource> visitedFoodSources = new HashSet<FoodSource>();
		double bestFitness = 0;
		FoodSource bestFoodSource = null;

		// recupera quais features s�o utilizadas por essa fonte de alimento
		boolean features[] = foodSource.getFeatureInclusion();

		// guarda o n�mero de feaures sendo utilizadas
		int nrFeatures = foodSource.getNrFeatures();
		int times = 0;
		FoodSource modifedFoodSource = null;
		do {
			times++;
			// Caso seja modificada apenas umna feature por vez
			if (perturbation.equals(PerturbationStrategy.CHANGE_ONE_FEATURE)) {
				int index = (int) Math.round(Math.random() * (featureSize - 1));
				if (!features[index]) {
					nrFeatures++;
					features[index] = true;
				}
				// Features ser�o removidas ou inclu�das controladas pelor
				// par�metro
				// MR
			} else if (perturbation.equals(PerturbationStrategy.USE_MR)) {
				for (int i = 0; i < featureSize; i++) {
					if (Math.random() < mr) {
						if (!features[i]) {
							nrFeatures++;
							features[i] = true;
						}
					}
				}
			} else {
				throw new RuntimeException("Invalid perturbation type "
						+ perturbation);
			}
			modifedFoodSource = new FoodSource(features);
		} while ((foodSources.contains(modifedFoodSource)
				|| neighbors.contains(modifedFoodSource)
				|| abandoned.contains(modifedFoodSource) || visitedFoodSources
					.contains(modifedFoodSource)) && times <= featureSize);

		if (!(foodSources.contains(modifedFoodSource)
				|| neighbors.contains(modifedFoodSource)
				|| abandoned.contains(modifedFoodSource) || visitedFoodSources
					.contains(modifedFoodSource))) {

			// calcula o fitness novo conjunto de features gerado
			states++;
			double fitness = calculateFitness(features);
			modifedFoodSource.setFitness(fitness);
			modifedFoodSource.setNrFeatures(nrFeatures);

			// atual � melhor que a vizinha que est� sendo explorada
			if (foodSource.getFitness() > fitness
					|| (fitness == foodSource.getFitness() && nrFeatures > foodSource
							.getNrFeatures())) {

				// incrementa o contador
				foodSource.incrementLimit();
				// se foi explorado mais que limit vezes abandona a fonte
				if (foodSource.getLimit() >= limit) {
					abandoned.add(foodSource);
				}
				visitedFoodSources.add(modifedFoodSource);
				// a vizinha � melhor que a atual
			} else {
				// se o fitness dessa fonte � melhor que o melhor j�
				// encontrado
				// armazena, se for igual ao melhor armazenado verifica qual
				// dos
				// dois utliza o menor nr de features
				if (fitness > bestFitness
						|| (fitness == bestFitness && nrFeatures < bestFoodSource
								.getNrFeatures())) {
					bestFoodSource = new FoodSource(modifedFoodSource);
					bestFitness = fitness;
				}
				markedToRemoved.add(foodSource);
				neighbors.add(modifedFoodSource);
			}

		}
		return new BeeParallelExecutionResult(markedToRemoved, neighbors,
				abandoned, visitedFoodSources, bestFoodSource, bestFitness);
	}

	/**
	 * Cria uma nova fonte de alimento pelo scout
	 */
	private void createScoutBee() {
		boolean features[] = new boolean[featureSize];
		Random random = new Random();
		FoodSource foodSource = null;
		int nrFeatures = 0;
		// novas features
		for (int j = 0; j < featureSize; j++) {
			boolean inclusion = random.nextBoolean();
			if (inclusion) {
				nrFeatures++;
			}
			features[j] = inclusion;
		}

		double curFitness = calculateFitness(features);
		// cria uma nova fonte
		foodSource = new FoodSource(features, curFitness, nrFeatures);
		// se n�o existe ainda adiciona na lista de scout para ser usada
		// posteriormente
		if (!(foodSources.contains(foodSource)
				|| abandoned.contains(foodSource) || visitedFoodSources
					.contains(foodSource))) {
			states++;
			scouts.add(foodSource);
		}
	}

	/**
	 * Ap�s enviar as employed e Onlookers bees remove todas as fontes
	 * abandonadas e adiciona as novas fontes encontradas pelos scout bees
	 */
	private void sendScoutBeesAndRemoveAbandonsFoodSource() {
		foodSources.removeAll(abandoned);
		foodSources.addAll(scouts);
	}

	/**
	 * Calcula a qualidade de uma fonte de alimento atrav�s da fonte selecionada
	 * 
	 * @param features
	 *            Features utilizadas para o c�lculo da qualidade da fonte de
	 *            alimento
	 * @return valor de 0 a 100 que indica a qualidade da fonte
	 */
	private double calculateFitness(boolean features[]) {

		ClassifierExecutor executor = new KFoldClassifierExecutor(knnClassifier);
		// carrega as features
		executor.setOriginalInstances(originalInstances);
		executor.loadFeatures();
		// chama o classificador e rtorna a acur�cia
		return executor.execute(features, KFOLD);
	}

	public void logFeatureSeletionInit(int runtime, int limit, double mr,
			PerturbationStrategy perturbation, int nrFeatures) {

		try {
			writer.write("Feature Selection START--------------------------------------------------------------------------------------------------------");

			writer.newLine();
			writer.write("Runtime [" + runtime + "], Limit [" + limit
					+ "], MR [" + mr + "], perturbation [" + perturbation);
			writer.newLine();
		} catch (IOException e) {
			throw new RuntimeException();
		}
	}

	public void logBestSolutionAndExecutionTime(long time) {
		try {
			writer.write("Best " + bestFoodSource);
			writer.newLine();
			writer.write("Executado em " + time + " percorrendo " + states
					+ " estados");
			writer.newLine();
			writer.write("Feature Selection END----------------------------------------------------------------------------------------------------------");
			writer.newLine();
		} catch (IOException e) {
			throw new RuntimeException();
		}
	}
	
	public void logBestSolutionEachIteration(int iteration) {
		try {
			writer.write("Best " + bestFoodSource);
			writer.newLine();
			writer.write("Executado na itera��o " + iteration );
			writer.newLine();
		} catch (IOException e) {
			throw new RuntimeException();
		}
	}


	/**
	 * Usado para paralelizar a execu��o de classifica��o
	 * 
	 */
	private class ClassifierExecutionCallable implements Callable<FoodSource> {

		private boolean[] features;

		public ClassifierExecutionCallable(boolean[] features,
				Filter... filters) {
			this.features = features;
		}

		@Override
		public FoodSource call() throws Exception {
			FoodSource f = new FoodSource(features, calculateFitness(features),
					1);
			return f;
		}

	}

	/**
	 * Usado para paralelizar o envio de abelhas a uma fonte de alimento
	 * 
	 */
	private class SendBeeCallable implements
			Callable<BeeParallelExecutionResult> {

		private FoodSource foodSource;

		public SendBeeCallable(FoodSource foodSource) {
			this.foodSource = foodSource;
		}

		@Override
		public BeeParallelExecutionResult call() throws Exception {
			return sendBee(foodSource);
		}

	}

}
