package br.unicamp.ic.featureselection;

/**
 * Indica algumas possibilidades de execu��o da sele��o de features
 * 
 * @author Mauricio Schiezaro
 */
public enum PerturbationStrategy {
	/**
	 * Indica que na explora��o da vizinhan�a o par�metro MR que indica quantas
	 * features ser�o alteradas na composi��odde um novo conjunto
	 */
	USE_MR,
	/**
	 * Indica que na explora��o da vizinhan�a apenas uma feature ser� adiciona
	 * ao conjunto da fonte que foi explorada, que ser� escolhida aleatoriamente
	 */
	CHANGE_ONE_FEATURE
}
