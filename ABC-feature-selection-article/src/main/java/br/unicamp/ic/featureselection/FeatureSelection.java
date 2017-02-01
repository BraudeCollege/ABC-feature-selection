package br.unicamp.ic.featureselection;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import br.unicamp.ic.classifier.ClassifierExecutor;
import br.unicamp.ic.featureselection.swarm.FoodSource;
import br.unicamp.ic.util.FileUtil;

/**
 * Respons�vel por fazer a sele��o de features
 * 
 * @author Mauricio Schiezaro
 */
public class FeatureSelection {

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
	 * Visited
	 */
	private Set<FoodSource> visitedFoodSources;

	/**
	 * Respons�vel por executar a classifica��o
	 */
	private ClassifierExecutor executor;

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
	 * Armazena os as fontes que n�o ser�o mais verificadas para serem removidas
	 * logo em seguida
	 */
	private Set<FoodSource> markedToRemoved;

	/**
	 * Armazena as fontes vizinhas que ser�o consideradas na busca
	 */
	private Set<FoodSource> neighbors;

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
	public FeatureSelection(int runtime, int limit, double mr,
			ClassifierExecutor executor) {

		this.runtime = runtime;
		this.limit = limit;
		this.mr = mr;
		bestFitness = 0;
		featureSize = executor.getFeatureSize();
		foodSources = new HashSet<FoodSource>();
		this.executor = executor;
		if (mr > 0) {
			perturbation = PerturbationStrategy.USE_MR;
		} else {
			perturbation = PerturbationStrategy.CHANGE_ONE_FEATURE;
		}
		abandoned = new HashSet<FoodSource>();
		fileUtil = FileUtil.newInstance();
		writer = fileUtil.getWriter();
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
	public FeatureSelection(int runtime, int limit, ClassifierExecutor executor) {
		this(runtime, limit, 0, executor);
	}

	/**
	 * Executa todo o processo de sele��o
	 */
	public void execute() {
		visitedFoodSources = new HashSet<FoodSource>();
		states = 0;
		logFeatureSeletionInit(runtime, limit, mr, perturbation,
				executor.getFeatureSize());
		long time = System.currentTimeMillis();
		initializeFoodSources();
		for (int i = 0; i < runtime; i++) {
			sendEmployedBees();
			fileUtil.flush();
			sendOnlookerBees();
			fileUtil.flush();
			sendScoutBeesAndRemoveAbandonsFoodSource();
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

		for (int i = 0; i < featureSize; i++) {
			states++;
			boolean features[] = new boolean[featureSize];
			features[i] = true;
			// calcula a qualidade do conjunto criado
			double curFitness = calculateFitness(features);
			// criamos a fonte de alimento
			FoodSource foodSource = new FoodSource(features, curFitness, 1);
			foodSources.add(foodSource);
			// armazena a melhor
			if (curFitness > bestFitness) {
				bestFoodSource = new FoodSource(foodSource);
				bestFitness = curFitness;
			}
		}
	}

	/**
	 * Envia as abelhas para explorar as fontes de alimento e sua vizinhan�a
	 */
	private void sendEmployedBees() {
		scouts = new HashSet<FoodSource>();
		markedToRemoved = new HashSet<FoodSource>();
		neighbors = new HashSet<FoodSource>();
		for (FoodSource foodSource : foodSources) {
			sendBee(foodSource);
		}
		foodSources.removeAll(markedToRemoved);
		foodSources.addAll(neighbors);
	}

	/**
	 * Envia as abelhas para explorar as fontes de alimento e sua vizinhan�a a
	 * partir da probabilidade de de explora��o das fontes
	 */
	private void sendOnlookerBees() {
		markedToRemoved = new HashSet<FoodSource>();
		neighbors = new HashSet<FoodSource>();

		TreeSet<FoodSource> sortedFoodSources = new TreeSet<FoodSource>(
				foodSources);
		int lim = foodSources.size() / 2 ;
		int i = 0;

		for (FoodSource foodSource : sortedFoodSources) {
			i++;
			if (i < lim) {
				sendBee(foodSource);
			} else {
				foodSource.incrementLimit();
			}
		}

		foodSources.removeAll(markedToRemoved);
		foodSources.addAll(neighbors);
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
	private void sendBee(FoodSource foodSource) {

		// recupera quais features s�o utilizadas por essa fonte de alimento
		boolean features[] = foodSource.getFeatureInclusion();

		// guarda o n�mero de feaures sendo utilizadas
		int nrFeatures = foodSource.getNrFeatures();
		int times = 0;
		FoodSource modifedFoodSource = null;
		int perturbated = 0;
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
							perturbated++;
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
					markAbandonsFoodSource(foodSource);
					createScoutBee();
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
				|| neighbors.contains(foodSource)
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
	 * Marca a fonte para ser removida pois foi abandonada
	 * 
	 * @param foodSource
	 *            FOnte a ser removida da lista, pois foi abandonada
	 */
	private void markAbandonsFoodSource(FoodSource foodSource) {
		abandoned.add(foodSource);
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
		// carrega as features
		executor.loadFeatures();
		// chama o classificador e rtorna a acur�cia
		return executor.execute(features, KFOLD);
	}

	/**
	 * Atualiza o executor da classifica��o (fitness)
	 * 
	 * @param executor
	 *            Executor que retorna a qualidade da fonte de alimento
	 */
	public void setExecutor(ClassifierExecutor executor) {
		this.executor = executor;
		featureSize = this.executor.getFeatureSize();
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
}
