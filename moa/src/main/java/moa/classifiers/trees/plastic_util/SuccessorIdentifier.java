package moa.classifiers.trees.plastic_util;

import java.util.Objects;

public class SuccessorIdentifier {
    public static final double DEFAULT_NOMINAL_VALUE = -1.0;
    private final boolean isNumeric;
    private final Double selectorValue;
    private final Double referenceValue;
    private final boolean isLower;
    private int hashCode;

    public SuccessorIdentifier(boolean isNumeric, Double referenceValue, Double selectorValue, boolean isLower) {
        this.isNumeric = isNumeric;
        this.isLower = isLower;
        this.selectorValue = selectorValue;
        if (referenceValue == null)
            this.referenceValue = selectorValue;
        else
            this.referenceValue = referenceValue;
        this.hashCode = Objects.hash(this.isNumeric, this.referenceValue, this.selectorValue, this.isLower);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null)
            return false;
        if (getClass() == o.getClass()) {
            SuccessorIdentifier that = (SuccessorIdentifier) o;
            boolean equal = isNumeric() == that.isNumeric();
            equal &= isLower() == that.isLower();
            equal &= selectorValue == that.getSelectorValue();
            equal &= referenceValue == that.getReferencevalue();
            return equal;
        }
        Double that = (Double) o;
        if (isNumeric)
            return containsNumericAttribute(that);
        else {
            boolean result = Objects.equals(selectorValue, that);
            if (!result) {
                result = Objects.equals(selectorValue, DEFAULT_NOMINAL_VALUE);
            }
            return result;
        }
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public double getSelectorValue() {
        return selectorValue;
    }

    public double getReferencevalue() {
        return referenceValue;
    }

    private boolean matchesCategoricalValue(double attValue) {
        if (isNumeric)
            return false;
        return selectorValue == attValue;
    }

    private boolean containsNumericAttribute(double attValue) {
        if (!isNumeric)
            return false;
        return isLower ? attValue < selectorValue : attValue >= selectorValue;
    }

    public SuccessorIdentifier getOther() {
        if (!isNumeric)
            return null;
        return new SuccessorIdentifier(isNumeric, selectorValue, selectorValue, !isLower);
    }

    public boolean isNumeric() {
        return isNumeric;
    }

    public boolean isLower() {
        if (!isNumeric)
            return false;
        return isLower;
    }

    public Double getValue() {
        return selectorValue;
    }
}
