package core;

public abstract class VoxelContainer 
{
	public final int RES;
	public VoxelContainer(int res)
	{
		this.RES=res;
	}
	public abstract int get(int x,int y,int z);
	public abstract void set(int x,int y,int z,int[] data);
}
