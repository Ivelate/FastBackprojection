package comparison.backprojection;

import java.util.concurrent.atomic.DoubleAdder;

public class BackprojectionConcurrentValueStorer implements BackprojectionValueStorer
{
	private DoubleAdder[][][] data;
	public BackprojectionConcurrentValueStorer(int resolution)
	{
		this.data=new DoubleAdder[resolution][resolution][resolution];
		for(int x=0;x<resolution;x++) for(int y=0;y<resolution;y++) for(int z=0;z<resolution;z++) this.data[x][y][z]=new DoubleAdder();
	}
	@Override
	public double get(int x, int y, int z) 
	{
		return this.data[x][y][z].sum();
	}
	public DoubleAdder[][][] getConcreteDataset()
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
		this.data[x][y][z].add(value);
	}
	@Override
	public void set(int x, int y, int z, double value) {
		this.data[x][y][z].reset();
		this.data[x][y][z].add(value);
	}
}
