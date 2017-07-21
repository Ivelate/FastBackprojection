package comparison.backprojection;

import java.util.concurrent.Semaphore;

import org.lwjgl.util.vector.Vector3f;

import core.BoxBounds;
import file.TransientImage;

public class BackprojectionThread extends Thread
{
	private final TransientImage[] timgs;
	private final BoxBounds bounds;
	private final int threads;
	private final int threadID;
	
	private BackprojectionValueStorer results;
	private Semaphore lock;
	private BackprojectionMonitor monitor;
	
	public BackprojectionThread(TransientImage[] timgs,BoxBounds bounds,int threads,int threadID,BackprojectionValueStorer results,Semaphore lock,BackprojectionMonitor monitor)
	{
		System.out.println("THREAD "+threadID+" ACTIVATED");
		this.timgs=timgs;	
		this.bounds=bounds;
		this.threads=threads;
		this.threadID=threadID;
		this.results=results;
		this.lock=lock;
		this.monitor=monitor;
	}
	public void run()
	{
		final int resolution=bounds.resolution;
		
		int currentLayer=-1;
		//Perform backprojection
		while((currentLayer=this.monitor.getLayer())>=0)
		{
			int z=currentLayer;
			System.out.println("T"+this.threadID+" - "+(((float)(z)/(this.monitor.getMaxLayer()))*100)+"%");
			for(int y=0;y<resolution;y++)
			{
				for(int x=0;x<resolution;x++)
				{
					//Taking half pixels into account
					float fx=bounds.xi+((float)(x+0.5f)/resolution)*bounds.sx;	
					float fy=bounds.yi+((float)(y+0.5f)/resolution)*bounds.sy;		
					float fz=bounds.zi+((float)(z+0.5f)/resolution)*bounds.sz;
					
					this.results.add(x,y,z,sumTransientIntensitiesFor(fx,fy,fz));
				}
			}
		}
		
		System.out.println("T"+this.threadID+" - Finished");
		this.lock.release();
	}
	
	private double sumTransientIntensitiesFor(float fx,float fy,float fz)
	{
		Vector3f voxel=new Vector3f(fx,fy,fz);
		double intensities=0;
		for(TransientImage img:this.timgs)
		{
			for(int h=0;h<img.height;h++)
			{
				Vector3f wallPoint=img.getPointForCoord(h);
				
				//time= d(laser,point) + d(point,wall)
				float time=	(float)(Math.sqrt(Vector3f.sub(img.getLaser(),voxel,null).lengthSquared())+
							Math.sqrt(Vector3f.sub(voxel,wallPoint,null).lengthSquared()));
				
				intensities+=img.getIntensityForTime(h, time);
			}
		}
		
		return intensities;
	}
}
