package file;

public class ConfidenceStorage 
{
	private long[] contents=null;
	
	public boolean isEmpty()
	{
		return contents==null;
	}
	
	public void accumulate(int[] data)
	{
		if(contents==null){
			System.out.println("Enabling high load CPU dumping. Memory cost will spike up");
			contents=new long[data.length];
		}
		
		for(int i=0;i<data.length;i++) contents[i]+=data[i];
	}
	
	public long[] getContents()
	{
		return this.contents;
	}
	
	/**
	 * Pretty shitty work-around
	 */
	public int[] contentsToIntegerList()
	{
		long maxVal=0;
		for(int i=0;i<this.contents.length;i++) if(this.contents[i]>maxVal) maxVal=this.contents[i];
		int[] ret=new int[this.contents.length];
		
		System.out.println("Volume max value: "+maxVal);
		if(maxVal<(long)(Integer.MAX_VALUE)*2+1)
		{
			for(int i=0;i<this.contents.length;i++) ret[i]=(int)this.contents[i];
		}
		else
		{
			double factor=((long)(Integer.MAX_VALUE)*2+1) / (((double)maxVal)+0.5);
			for(int i=0;i<this.contents.length;i++) ret[i]=(int)(this.contents[i]*factor);
		}
		
		return ret;
	}
}
