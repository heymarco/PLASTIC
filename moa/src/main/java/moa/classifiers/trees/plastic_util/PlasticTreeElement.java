package moa.classifiers.trees.plastic_util;

public class PlasticTreeElement {
    private PlasticNode node;
    private SuccessorIdentifier key;

    public PlasticTreeElement(PlasticNode node, SuccessorIdentifier key) {
        this.node = node;
        this.key = key;
    }

    public PlasticNode getNode() {
        return node;
    }

    public SuccessorIdentifier getKey() {
        return key;
    }
}
