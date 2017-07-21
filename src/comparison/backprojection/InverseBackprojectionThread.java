package comparison.backprojection;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.DoubleAdder;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import core.BoxBounds;
import file.TransientImage;
import file.TransientInfo;

public class InverseBackprojectionThread extends Thread
{
	private final static Vector3f WALL_PERPENDICULAR=new Vector3f(0,0,1);
	
	private final TransientImage[] timgs;
	private final BoxBounds bounds;
	private final int threads;
	private final int threadID;
	
	private BackprojectionValueStorer results;
	private Semaphore lock;
	
	public InverseBackprojectionThread(TransientImage[] timgs,BoxBounds bounds,int threads,int threadID,BackprojectionValueStorer results,Semaphore lock)
	{
		System.out.println("THREAD "+threadID+" ACTIVATED");
		this.timgs=timgs;	
		this.bounds=bounds;
		this.threads=threads;
		this.threadID=threadID;
		this.results=results;
		this.lock=lock;
	}
	public void run()
	{
		//final int resolution=bounds.resolution;
		final float imgsLength=(float)timgs.length;
		
		float xif=(imgsLength/this.threads)*this.threadID;
		int xf=(int)(xif+(imgsLength/this.threads));
		int xi=(int)xif;
		System.out.println(this.threadID+" "+xif+" "+xf+" "+xi);
		//Perform backprojection
		for(int i=xi;i<xf;i++)
		{
			System.out.println("T"+this.threadID+" - "+(((float)(i-xi)/(xf-xi))*100)+"%");
			for(int y=0;y<timgs[i].height;y++)
			{
				for(int x=0;x<timgs[i].width;x++)
				{
					TransientInfo tinf=timgs[i].get(x, y);
					float intensity=tinf.color[0];
					if(intensity>0)
					{
						Matrix4f[] matrixes=generateMatrixesForEllipsoid(tinf.time,timgs[i].getLaser(),tinf.point);
						accumulateEllipsoidOverResult(matrixes[0],matrixes[1],intensity);
					}
				}
			}
		}
		
		System.out.println("T"+this.threadID+" - Finished");
		this.lock.release();
	}
	
	/**
	 * ret[0]=forward matrix
	 * ret[1]=inverse matrix
	 */
	private Matrix4f[] generateMatrixesForEllipsoid(float time,Vector3f laserPos,Vector3f wallPos)
	{//|TODO 
		Vector3f translation=new Vector3f((laserPos.x+wallPos.x)/2,(laserPos.y+wallPos.y)/2,0); //System.out.println(translation);
		double focalDist=Math.sqrt((laserPos.x-wallPos.x)*(laserPos.x-wallPos.x) + (laserPos.y-wallPos.y)*(laserPos.y-wallPos.y)); //System.out.println(focalDist);
		float scaleyz=(float)Math.sqrt((time*time -focalDist*focalDist)/4);
		Vector3f scale=new Vector3f(
				time/2,
				scaleyz,
				scaleyz); //... i think 
		//System.out.println(scale);
		float rotation=(float)Math.atan2(laserPos.y-wallPos.y, laserPos.x-wallPos.x); //Definitely not sure about this one either
		//System.out.println(rotation);
		Matrix4f[] ret={(new Matrix4f()).translate(translation).rotate(rotation, WALL_PERPENDICULAR).scale(scale),
						(new Matrix4f()).scale(new Vector3f(1/scale.x,1/scale.y,1/scale.z)).rotate(-rotation, WALL_PERPENDICULAR).translate(new Vector3f(-translation.x,-translation.y,-translation.z))};
		return ret;
	}
	
	private void accumulateEllipsoidOverResult(Matrix4f fwd,Matrix4f bwd,double intensity)
	{
		for(int x=0;x<this.results.getResolution();x++)
		{
			for(int y=0;y<this.results.getResolution();y++)
			{
				//Lets find Z
				float rx=this.bounds.xi+(this.bounds.sx*(x+0.5f)/this.results.getResolution());
				float ry=this.bounds.yi+(this.bounds.sy*(y+0.5f)/this.results.getResolution());
				Vector4f acc=new Vector4f(rx,ry,0,1);
				Matrix4f.transform(bwd, acc, acc);
				acc.z=1 - acc.y*acc.y -acc.x*acc.x;
				if(acc.z<0) continue;
				acc.z=(float)Math.sqrt(acc.z);
				Matrix4f.transform(fwd, acc, acc);
				int zi= (int)((acc.z - this.bounds.zi) *this.results.getResolution()/this.bounds.sz - 0.499f);
				if(zi>=0&&zi<this.results.getResolution()) this.results.add(x,y,zi,intensity);
			}
		}
	}
}
