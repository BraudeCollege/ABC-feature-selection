package br.unicamp.ic.execution.knn;

import java.io.FileNotFoundException;
import java.io.IOException;

import weka.classifiers.Classifier;
import weka.classifiers.functions.LibSVM;

import br.unicamp.ic.classifier.CVParameterSelectionExecutor;
import br.unicamp.ic.classifier.ClassifierExecutor;

/**
 * Esta execu��o percorre todas as combina��es poss�veis no conhjuntod e dados
 * da iris (16 combina��es) e imprime o resultado de classifica��o utilizando
 * 10-fold para podermos comparar se o resultado pela sele��o de caracter�stica
 * pega as melhores features
 * 
 * @author Mauricio Schiezaro
 */
public class IrisAllCombinationExecution {

	public static void main(String[] args) throws FileNotFoundException,
			IOException {

		final int KFOLD = 10;
		final String datasetFilename = "iris.arff"; 

		Classifier classifier = new LibSVM();
		ClassifierExecutor executor = new CVParameterSelectionExecutor(classifier);
		executor.loadFeatures(datasetFilename);
		boolean features[] = new boolean[4];


		// Classifica��o utilizando a feature 4
		features[3] = true;
		System.out.println("Classifica��o utilizando a  feature  4");
		executor.execute(features, KFOLD);

		// Classifica��o utilizando a feature 3
		features[2] = true;
		features[3] = false;
		executor.loadFeatures();
		System.out.println("Classifica��o utilizando a  feature  3");
		executor.execute(features, KFOLD);		

		// Classifica��o utilizando as features 3 e 4
		features[3] = true;
		executor.loadFeatures();
		System.out.println("Classifica��o utilizando as features 3 e 4");
		executor.execute(features, KFOLD);

		// Classifica��o utilizando a feature 2
		features[1] = true;
		features[2] = false;
		features[3] = false;
		executor.loadFeatures();
		System.out.println("Classifica��o utilizando a  feature  2");
		executor.execute(features, KFOLD);

		// Classifica��o utilizando as features 2 e 4
		features[3] = true;
		executor.loadFeatures();
		System.out.println("Classifica��o utilizando as features 2 e 4");
		executor.execute(features, KFOLD);

		// Classifica��o utilizando as features 2 e 3
		features[2] = true;
		features[3] = false;
		executor.loadFeatures();  
		System.out.println("Classifica��o utilizando as features 2 e 3");
		executor.execute(features, KFOLD);

		// Classifica��o utilizando as features 2,3 e 4
		features[3] = true;
		executor.loadFeatures();
		System.out.println("Classifica��o utilizando as features 2,3 e 4");
		executor.execute(features, KFOLD);

		// Classifica��o utilizando a feature 1
		features[0] = true;
		features[1] = false;
		features[2] = false;
		features[3] = false;
		executor.loadFeatures();
		System.out.println("Classifica��o utilizando a  feature  1");
		executor.execute(features, KFOLD);

		// Classifica��o utilizando as features 1 e 4
		features[3] = true;
		executor.loadFeatures();
		System.out.println("Classifica��o utilizando as features 1 e 4");
	    executor.execute(features, KFOLD);

		// Classifica��o utilizando as features 1 e 3
		features[2] = true;
		features[3] = false;
		executor.loadFeatures();
		System.out.println("Classifica��o utilizando as features 1 e 3");
		executor.execute(features, KFOLD);		

		// Classifica��o utilizando as features 1,3 e 4
		features[3] = true;
		executor.loadFeatures();
		System.out.println("Classifica��o utilizando as features 1,3 e 4");
		executor.execute(features, KFOLD);

		// Classifica��o utilizando as features 1 e 2
		features[1] = true;
		features[2] = false;
		features[3] = false;
		executor.loadFeatures();
		System.out.println("Classifica��o utilizando as features 1 e 2");
	    executor.execute(features, KFOLD);

		// Classifica��o utilizando as features 1, 2 e 4
		features[3] = true;
		executor.loadFeatures();
		System.out.println("Classifica��o utilizando as features 1,2 e 4");
	    executor.execute(features, KFOLD);		

		// Classifica��o utilizando as features 1,2 e 3
		features[2] = true;
		features[3] = false;
		executor.loadFeatures();
		System.out.println("Classifica��o utilizando as features 1,2 e 3");
	    executor.execute(features, KFOLD);		

		// Classifica��o utilizando as features 1,2,3 e 4
		features[3] = true;
		executor.loadFeatures();
		System.out.println("Classifica��o utilizando as features 1,2,3 e 4");
	    executor.execute(features, KFOLD);		
	}

}
