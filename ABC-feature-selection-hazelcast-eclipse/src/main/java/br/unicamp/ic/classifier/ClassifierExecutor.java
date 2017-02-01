package br.unicamp.ic.classifier;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import weka.core.Instances;
import weka.filters.Filter;

/**
 * Define a excu��o de um classificador
 * 
 * @author Mauricio Schiezaro
 * 
 */
public abstract class ClassifierExecutor {
	
	/**
	 * Dados que ser�o carregados dos arquivos
	 */
	protected Instances originalInstances;

	/**
	 * Dados que s�o copiados de originalInstances, para podemos manipular quais
	 * atributos iremos utlizar em cada execu��o.
	 */
	protected Instances instances;

	/**
	 * Carrega do arquivo os dados que ser�o classificados. Esse m�todo �
	 * chamado uma �nica vez para carregarmos os dados do arquivo, depois para
	 * recuperar os dados originais � s� chamar o m�todo loadFeatures() sem
	 * par�metros que os dados ser�o recuperados da mem�ria.
	 * 
	 * @param filename
	 *            Nome do arquivo que ser� carregado
	 * @param Filtros
	 *            no pr�-processamento da feature. Exemplos: normaliza��o,
	 *            Z-score, Replace Missing values
	 */
	public int loadFeatures(String filename, Filter... filter) {
		try {
			// carrega os dados do arquivo
			originalInstances = new Instances(new FileReader(
					System.getProperty("user.dir") + "/src/main/resources/"
							+ filename));
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
		// criamos sempre uma c�pia dos dados originais para manipularmos os
		// atributos
		instances = new Instances(originalInstances);
		// retorna o n�mero de atributos carregados do arquivo
		return originalInstances.numAttributes() - 1;		
	}
	
	/**
	 * Carrega do arquivo os dados que ser�o classificados. Esse m�todo �
	 * chamado uma �nica vez para carregarmos os dados do arquivo, depois para
	 * recuperar os dados originais � s� chamar o m�todo loadFeatures() sem
	 * par�metros que os dados ser�o recuperados da mem�ria.
	 * 
	 * @param filename
	 *            Nome do arquivo que ser� carregado
	 */
	public int loadFeatures(String filename) {
		Filter[] filter =  new Filter[0];
		return loadFeatures(filename, filter);
	}
	
	/**
	 * Carrega os dados originais da m�moria.
	 */
	public void loadFeatures() {
		instances = new Instances(originalInstances);
	}
	
	/**
	 * Retorna o n�mero features dos dados originais carregados do arquivo
	 */
	public int getFeatureSize() {
		if (originalInstances == null) {
			return -1;
		}
		return originalInstances.numAttributes() - 1;
	}
	
	public void setOriginalInstances(Instances originalInstances) {
		this.originalInstances = originalInstances;
	}

	/**
	 * Executa a classifica��o. Os dados de treinamento e teste utilizam k-fold
	 * onde o n�mero de k � indicado pelo par�metro kfold, e as features que
	 * ser�o utilizadas na classifica��o s�o indicadas pelo vetor
	 * featureInclusion. Cada posi��o do vetor indica uma feature e o valor true
	 * indica que ela participar� da classifica��o, false n�o participar�
	 * @param featureInclusion  features que ser�o utilizadas na classifica��o
	 * @param k Par�metro k-fold para dividir dados de treinamento e teste
	 */
	public abstract double execute(boolean[] featureInclusion, int k);
	
	/**
	 * Executa a classifica��o. Os dados de treinamento e teste utilizam k-fold
	 * onde o n�mero de k � indicado pelo par�metro kfold, e as features que
	 * ser�o utilizadas na classifica��o s�o indicadas pelo vetor
	 * featureInclusion. Cada posi��o do vetor indica uma feature e o valor true
	 * indica que ela participar� da classifica��o, false n�o participar�
	 * @param featureInclusion  features que ser�o utilizadas na classifica��o
	 * @param k Par�metro k-fold para dividir dados de treinamento e teste
	 * @param classIndex indica qual coluna est� o label da classifica��o (ground truth)
	 */	
	public abstract double execute(boolean[] featureInclusion, int kFold, int classIndex);
}
