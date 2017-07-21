package core;

public class FilterResults 
{
	public final double[][] maxResult;
	public final int[][] maxResultDepth;
	public final double maxIntensity;
	public FilterResults(double[][] maxResult,int[][] maxResultDepth,double maxIntensity)
	{
		this.maxResult=maxResult;
		this.maxResultDepth=maxResultDepth;
		this.maxIntensity=maxIntensity;
	}
}
