package mops;

public class FeatureMatch implements Comparable {

	public final Feature feature1;
	public final Feature feature2;

	public final float distance;

	public final String name;

	public FeatureMatch(Feature f1, Feature f2, String name) {
		this.feature1 = f1;
		this.feature2 = f2;
		this.name = name;
		this.distance = f1.descriptorDistance(f2);
	}

	public int compareTo(Object other) {
		FeatureMatch fother = (FeatureMatch)other;
		if(this.distance < fother.distance) return -1;
		if(this.distance == fother.distance) return 0;
		else return 1;
	}
}

