package moa.classifiers.trees.plastic_util;

import moa.classifiers.core.conditionaltests.InstanceConditionalTest;

import java.util.*;

public class Successors {
    private Double referenceValue;
    private HashMap<SuccessorIdentifier, PlasticNode> successors = new HashMap<>();

    public Successors(boolean isBinarySplit, boolean isNumericSplit) {
        this.isBinarySplit = isBinarySplit;
        this.isNumericSplit = isNumericSplit;
    }

    private final boolean isBinarySplit;
    private final boolean isNumericSplit;


    public boolean addSuccessorNumeric(Double attValue, PlasticNode n, boolean isLower) {
        if (!isNumericSplit)
            return false;
        if (successors.size() >= 2)
            return false;
        if (referenceValue != null && !referenceValue.equals(attValue))
            return false;

        SuccessorIdentifier id = new SuccessorIdentifier(true, attValue, attValue, isLower);
        if (successors.containsKey(id))
            return false;

        referenceValue = attValue;
        successors.put(id, n);
        return true;
    }


    public boolean addSuccessorNominalBinary(Double attValue, PlasticNode n) {
        if (isNumericSplit)
            return false;
        if (!isBinarySplit)
            return false;
        if (successors.size() >= 2)
            return false;
        if (successors.size() == 1) {
            SuccessorIdentifier key = (SuccessorIdentifier) successors.keySet().toArray()[0];  // get key of existing successor
            if (key.getValue() == SuccessorIdentifier.DEFAULT_NOMINAL_VALUE) { // check if the key is the default key for nominal values
                if (!referenceValue.equals(attValue)) // if the key is the default key, only add the successor if the referenceValue of the split matches the provided value
                    return false;
            }
        }

        SuccessorIdentifier id = new SuccessorIdentifier(false, attValue, attValue, false);
        if (successors.containsKey(id))
            return false;

        referenceValue = attValue;
        successors.put(id, n);
        return true;
    }

    public boolean addDefaultSuccessorNominalBinary(PlasticNode n) {
        if (isNumericSplit)
            return false;
        if (!isBinarySplit)
            return false;
        if (successors.size() >= 2)
            return false;

        SuccessorIdentifier id = new SuccessorIdentifier(false, referenceValue, SuccessorIdentifier.DEFAULT_NOMINAL_VALUE, false);
        if (successors.containsKey(id))
            return false;

        successors.put(id, n);
        return true;
    }

    public boolean addSuccessorNominalMultiway(Double attValue, PlasticNode n) {
        if (isNumericSplit)
            return false;
        if (isBinarySplit)
            return false;

        SuccessorIdentifier id = new SuccessorIdentifier(false, attValue, attValue, false);
        if (successors.containsKey(id))
            return false;

        successors.put(id, n);
        return true;
    }

    public PlasticNode getSuccessorNode(SuccessorIdentifier key) {
        return successors.get(key);
    }

    public PlasticNode getSuccessorNode(Double attributeValue) {
        for (SuccessorIdentifier s : successors.keySet()) {
            if (s.equals(attributeValue))
                return successors.get(s);
        }
        return null;
    }

    public SuccessorIdentifier getSuccessorKey(Object key) {
        //TODO: Looping over a set is probably not the best way to do this.
        for (SuccessorIdentifier successorKey : successors.keySet()) {
            if (successorKey.equals(key)) {
                return successorKey;
            }
        }
        return null;
    }

    public boolean isNominal() {
        return !isNumericSplit;
    }

    public boolean isBinary() {
        return isBinarySplit;
    }

    public boolean contains(Object key) {
        return successors.containsKey(key);
    }

    public Double getReferenceValue() {
        return referenceValue;
    }

    public int size() {
        return successors.size();
    }

    public SuccessorIdentifier getMissingKey() {
        if (successors.size() > 1)
            return null;
        SuccessorIdentifier someKey = (SuccessorIdentifier) successors.keySet().toArray()[0];
        return someKey.getOther();
    }

    public boolean lowerIsMissing() {
        SuccessorIdentifier key = getMissingKey();
        if (key == null)
            return false;
        return key.isLower();
    }

    public boolean upperIsMissing() {
        SuccessorIdentifier key = getMissingKey();
        if (key == null)
            return false;
        return !key.isLower();
    }

    public void adjustThreshold(double newThreshold) {
        HashMap<SuccessorIdentifier, PlasticNode> newSuccessors = new HashMap<>();
        for (SuccessorIdentifier oldId: successors.keySet()) {
            SuccessorIdentifier newId = new SuccessorIdentifier(true, newThreshold, newThreshold, oldId.isLower());
            newSuccessors.put(newId, successors.get(oldId));
        }
        successors = newSuccessors;
    }

    public Collection<PlasticNode> getAllSuccessors() {
        return successors.values();
    }

    public Set<SuccessorIdentifier> getKeyset() {
        return successors.keySet();
    }
}
