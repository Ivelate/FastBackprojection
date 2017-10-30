package core;

import java.io.File;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import file.AcceptedFileName;
import file.TransientImage;

/**
 * Program parameters
 */
public class TransientVoxelizationParams 
{
	//General params
	public boolean VERBOSE=false;		//Write things in console
	public int VOXEL_RESOLUTION=128;	//Number of voxels (total = VOXEL_RESOLUTION ^3)
	public int DELAYED_RENDER_BATCH_SIZE=160000; //If the program explodes, reduce
	public int SPHERE_MAX_RECURSIONS=7;
	public boolean MEMORY_SAVING_MODE=false; //If true, loads streaks twice. If false, loads streaks once and conserves them in memory. Leave as false if you have enough RAM
	public int MAX_INTENSITY_MULTIPLIER=255; // 0 to 255 (why would somebody want changing that?)
	public float ERROR_THRESHOLD_WEIGHT=1; //Maximum ellipsoid approximation error, in voxel units
	public int ELLIPSOIDS_PER_PIXEL=1; 
	public boolean STOCHASTIC=false; //If ellipsoids are extended randomly over the temporal domain
	public float ELLIPSOID_PER_PIXEL_THRESHOLD_WEIGHT=1; //Extends this number of ellipsoids per voxel unit. Calculates ELLIPSOIDS_PER_PIXEL automatically with delta time and voxelSize. If <= 0, disabled
	public boolean USE_HALF_ELLIPSOIDS=true; //Considering that only half of an ellipsoid can be seen at the same time (because the wall occludes the rest) this can be used to save computations. If some of the scene wall points doesn't share the same normal THIS NO LONGER APPLIES!! so it should be set at false at expense of computation power
											 //(and next time record just a wall pls)
	public boolean ALLOW_OVERFLOW_PROTECTION=true; //If a risk of overflow in GPU is detected the program will auto-dump the 3D texture contents into CPU
	public float CLAMP_INTENSITY_GREATER_THAN=-1; //Max streak intensity threshold to consider, all values above this are discarded (none are discarded if this value is <0)
	public boolean NORMALIZE_TO_UNIT_INTERVAL=true; //If true, the program will output probabilities in the 0..1 interval regardless of the radiance of the input streaks

	//UNIMPLEMENTED public float CLAMP_TIME_COORD_LESS_THAN=-1; //Min time coord to consider, all values above this are discarded (none are discarded if this value is <0)
	
	//Ortho matrix size
	public Matrix4f orthoMatrix=null; //It can be custom-provided
	public float voxelSize=0; //Only need to set if if orthoMatrix!=null
	public float ORTHO_OFFSETX=-0.55f; 	public float ORTHO_SIZEX=0.6f; //
	public float ORTHO_OFFSETY=-0.3f; 	public float ORTHO_SIZEY=0.6f; // ALL THIS THREE SIZES ARE CURRENTLY SET AS THE SAME VALUE WHEN USEN -ortho
	public float ORTHO_OFFSETZ=1f; 	public float ORTHO_SIZEZ=0.6f;	   //
	
	//Geometry config
	public float streakYratio=1; //Relation between X and Y spatial streak sizes (Y/X ratio)
	public float fov=(float)Math.toRadians(90);
	public Vector3f camera=new Vector3f(-0.2f,0,1);
	public Vector3f lookTo=new Vector3f(-0.2f,0,0);
	public Vector3f laserOrigin=new Vector3f(-0.2f,0,1);
	public Vector3f wallNormal=new Vector3f(0,0,1); //Normalized
	public Vector3f wallDir=new Vector3f(1,0,0); //Normalized (x spatial axis of the streak images)
	public float t_delta=0.001f; //cam exposure
	public float t0=0;
	public boolean UNWARP_LASER=false; //True if no streak distance laser->wall is to be considered
	public boolean UNWARP_CAMERA=false; //True if no streak distance wall->camera is to be considered
	
	public Vector3f[] lasers={
			new Vector3f(-0.2f,0,0),
			new Vector3f(0.2f,0.2f,0),
			new Vector3f(0.2f,0.4f,0),
			new Vector3f(0.2f,0.6f,0),
			
			new Vector3f(-0.1f,0,0),
			new Vector3f(-0.1f,0.2f,0),
			new Vector3f(-0.1f,0.4f,0),
			new Vector3f(-0.1f,0.6f,0),
			
			new Vector3f(-0.4f,0,0),
			new Vector3f(-0.4f,0.2f,0),
			new Vector3f(-0.4f,0.4f,0),
			new Vector3f(-0.4f,0.6f,0),
			
			new Vector3f(-0.7f,0,0),
			new Vector3f(-0.7f,0.2f,0),
			new Vector3f(-0.7f,0.4f,0),
			
			new Vector3f(0.5f,0,0),
			new Vector3f(0.5f,0.2f,0),
			new Vector3f(0.5f,0.4f,0),
			};
	
	
	//INPUT
	public File inputFolder=new File("../../2016_LookingAroundCorners/bunny_final_multilaser_2b_highres"); 	//Input folder
	public boolean READ_HDR_FILES=true;
	public AcceptedFileName acceptedFileName=new AcceptedFileName(2,2,READ_HDR_FILES?".hdr":".float");	//Accepted input files
																										//Input format (current): SLICE_<laserIndex>_<scanlineIndex>.float
																										//								   ^ from 1 to max
																										//each float file contains an streak image
	public File lasersFile=null; //If null, lasers are received by command line input
	public boolean readLasersAsText=false; //if false, read the file in a binary format <float><float><float>... , if true, read the file in text format %d %d %d\n%d %d %d ...
	public File wallFile=null;
	public boolean readWallAsText=false;
	
	//OUTPUT
	public boolean DEFAULT_SAVE_AS_HDR=false; //If no image name is specified, details if the default output is HDR or not
	public File saveFolder=null; // null= execution folder
	public boolean saveImage=false;		public File filename2d=null; //Save image 2D flag, and name
	public boolean save2DRaw=false;	public File filename2draw=null; //Save image 2D without filtering
	public boolean printGrayscale=false; //Print images in grayscale instead of color
	public boolean save3DDump=false;	public File filename3d=null; //Save full 3D voxelization flag, and name
	public boolean save3DRaw=false;	public File filename3draw=null; //Save full 3D voxelization flag, and name without filtering
	public boolean backprojectCpu=true; //The GPU laplacian filter is faster but worse. You can implement a good GPU laplacian filter for maximum speed :D
	public File executionInfoFile=null;
	public boolean PRINT_TRANSIENT_IMAGES=false; //Normally used as debug. Prints each transient image used to reconstruct the final volume
	
	public Vector3f[] OVERRIDE_TRANSIENT_WALL_POINTS=null; //Overrides all transient image wall points for this values
	
	//Custom, not input-assignable params (only to be used through an special API)
	public boolean AUTO_CLEAN=true; //Auto calls cleanup() after code execution. Disable only if some data of the GPU needs to be accessed after the voxelization is performed. The cleanup will need to be performed manually later.
	public boolean FORCE_2D_BACKPROJECT=false; //Performs 2D backprojection even if no 2D image is being saved (virtually useless if not paired with AUTO_CLEAN=false)
	public boolean ENABLE_HARDWARE_CONSERVATIVE_RASTER=false; //Causes triangle vertex overloading if active, so results are usually beter if left disabled
	public boolean CLEAR_STORAGE_ON_INIT=true; //Clears to 0 the 3D storage when initializing it. In some GPUs it is already initialized to 0, so it could be disabled for a slight speed improvement
	
	public TransientImage[] CUSTOM_TRANSIENT_IMAGES=null; //Transient images to use instead of the ones on the input folder
	public boolean AUTO_MANAGE_DISPLAY=false;
	
	public void setOrthoSize(float size)
	{
		this.ORTHO_SIZEX=this.ORTHO_SIZEY=this.ORTHO_SIZEZ=size;
	}
	public float getMaxOrthoSize()
	{
		return Math.max(Math.max(this.ORTHO_SIZEX, this.ORTHO_SIZEY), this.ORTHO_SIZEZ);
	}
	
	/**
	 * Checks if all params are in their correct range and corrects them if neccesary
	 */
	public void validate()
	{
		//Ensure intensity multiplier is in range
		if(this.MAX_INTENSITY_MULTIPLIER<1) this.MAX_INTENSITY_MULTIPLIER=1;
		else if(this.MAX_INTENSITY_MULTIPLIER>255) this.MAX_INTENSITY_MULTIPLIER=255;
		
		wallNormal.normalise();
		wallDir.normalise();
		
		if(ELLIPSOIDS_PER_PIXEL<1) ELLIPSOIDS_PER_PIXEL=1;
	}
	
}
