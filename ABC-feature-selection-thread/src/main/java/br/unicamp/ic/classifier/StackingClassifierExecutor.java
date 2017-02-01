package br.unicamp.ic.classifier;

import java.util.Random;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.functions.LibSVM;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.StackingC;
import weka.core.neighboursearch.KDTree;

public class StackingClassifierExecutor extends ClassifierExecutor {

	private StackingC stacking;

	public StackingClassifierExecutor() {
		Classifier[] classifiers = new Classifier[2];
		// classificador KNN
		IBk ibk = new IBk();
		ibk.setCrossValidate(true);
		ibk.setKNN(4);
		ibk.setNearestNeighbourSearchAlgorithm(new KDTree());
		classifiers[0] = ibk;
		// classificador SVM
		classifiers[1] = new LibSVM();
		stacking = new StackingC();
		stacking.setClassifiers(classifiers);
	}

	/**
	 * Executa a classifica��o. Os dados de treinamento e teste utilizam k-fold
	 * onde o n�mero de k � indicado pelo par�metro kfold, e as features que
	 * ser�o utilizadas na classifica��o s�o indicadas pelo vetor
	 * featureInclusion. Cada posi��o do vetor indica uma feature e o valor true
	 * indica que ela participar� da classifica��o, false n�o participar�
	 * 
	 * @param featureInclusion
	 *            features que ser�o utilizadas na classifica��o
	 * @param k
	 *            Par�metro k-fold para dividir dados de treinamento e teste
	 */
	public double execute(boolean[] featureInclusion, int kFold) {

		int deletedFetures = 0;
		// Exclui as features que n�o devem participar do processo de
		// classifica��o
		for (int i = 0; i < featureInclusion.length; i++) {
			if (!featureInclusion[i]) {
				instances.deleteAttributeAt(i - deletedFetures);
				deletedFetures++;
			}
		}
		instances.setClassIndex(instances.numAttributes() - 1);
		// realiza cross-validation
		Evaluation eval = null;
		try {
			eval = new Evaluation(instances);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		try {
			eval.crossValidateModel(stacking, instances, kFold, new Random(1));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		// Retorna o valor da classifica��o
//		double[][] confusionMatrix = eval.confusionMatrix();
//		return calculateFMeasure(confusionMatrix);
		return eval.pctCorrect();
	}
	
	/**
	 * Executa a classifica��o. Os dados de treinamento e teste utilizam k-fold
	 * onde o n�mero de k � indicado pelo par�metro kfold, e as features que
	 * ser�o utilizadas na classifica��o s�o indicadas pelo vetor
	 * featureInclusion. Cada posi��o do vetor indica uma feature e o valor true
	 * indica que ela participar� da classifica��o, false n�o participar�
	 * 
	 * @param featureInclusion
	 *            features que ser�o utilizadas na classifica��o
	 * @param k
	 *            Par�metro k-fold para dividir dados de treinamento e teste
	 * @param classIndex
	 *            indica qual coluna est� o label da classifica��o (ground
	 *            truth)
	 */
	public double execute(boolean[] featureInclusion, int kFold, int classIndex) {

		int deletedFetures = 0;
		// Exclui as features que n�o devem participar do processo de
		// classifica��o
		for (int i = 0; i < featureInclusion.length; i++) {
			if (!featureInclusion[i]) {
				instances.deleteAttributeAt(i - deletedFetures);
				deletedFetures++;
			}
		}
		instances.setClassIndex(classIndex);

		// realiza cross-validation
		Evaluation eval = null;
		try {
			eval = new Evaluation(instances);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		try {
			eval.crossValidateModel(stacking, instances, kFold, new Random(1));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		// Retorna o valor da classifica��o
//		double[][] confusionMatrix = eval.confusionMatrix();
//		return calculateFMeasure(confusionMatrix);
		return eval.pctCorrect();		
	}
	
	public double calculateFMeasure(double[][] confusionMatrix) {
		return 0d;
	}
}
