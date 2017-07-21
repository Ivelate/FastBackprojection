package core;

public class ArrayVoxelContainer extends VoxelContainer
{
	private int[] data;
	public ArrayVoxelContainer(int res)
	{
		super(res);
	}
	public ArrayVoxelContainer(int res,int[] data)
	{
		super(res);
		this.data=data;
	}
	@Override
	public int get(int x, int y, int z) {
		return this.data[z*RES*RES + y*RES + x];
	}

	@Override
	public void set(int x, int y, int z, int[] data) {
		this.data=data;
	}

}
