package comparison.backprojection;

import java.util.concurrent.atomic.DoubleAdder;

public class BackprojectionNonConcurrentValueStorer implements BackprojectionValueStorer
{
	private double[][][] data;
	public BackprojectionNonConcurrentValueStorer(int resolution)
	{
		this.data=new double[resolution][resolution][resolution];
	}
	public BackprojectionNonConcurrentValueStorer(double[][][] data)
	{
		this.data=data;
	}
	@Override
	public double get(int x, int y, int z) 
	{
		return this.data[x][y][z];
	}
	public double[][][] getConcreteDataset()
	{
		return this.data;
	}
	@Override
	public int getResolution() {
		return data.length;
	}
	@Override
	public void add(int x, int y, int z, double value) 
	{
		this.data[x][y][z]+=value;
	}
	@Override
	public void set(int x, int y, int z, double value) {
		this.data[x][y][z]=value;
	}
}
