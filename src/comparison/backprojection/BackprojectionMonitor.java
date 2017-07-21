package comparison.backprojection;

public class BackprojectionMonitor 
{
	private int maxLayer;
	private int currentLayer=-1;
	
	public BackprojectionMonitor(int maxLayer)
	{
		this.maxLayer=maxLayer;
	}
	
	public synchronized int getLayer()
	{
		this.currentLayer++;
		if(this.currentLayer>this.maxLayer) return -1;
		return this.currentLayer;
	}
	public int getMaxLayer()
	{
		return this.maxLayer;
	}
}
