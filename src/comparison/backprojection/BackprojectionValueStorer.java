package comparison.backprojection;

public interface BackprojectionValueStorer 
{
	public double get(int x,int y,int z);
	public void add(int x,int y,int z,double value);
	public void set(int x,int y,int z,double value);
	public int getResolution();
}