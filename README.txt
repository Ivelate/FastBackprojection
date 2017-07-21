Fast Inverse Backprojection usage:

NOTE: The amount of memory reserved to java needs to be adjusted manually using -XX:MaxDirectMemorySize=4096m -Xms1024M -Xmx6132M
For an example of 4GB of RAM, our method is invoked including its libreries executing 

java  -XX:MaxDirectMemorySize=4096m -Xms1024M -Xmx4096M -cp TransientVoxelization_0_4.jar;transientVoxelizationLibs/* core.TransientVoxelization

PROGRAMS

core.TransientVoxelization <parameters> : Our method
comparison.backprojection.Backprojection <parameters> -threads numThreads : Velten et al. backprojection
visualizer.TransientVisualizer dumpFilePath : Visualizes a .dump file in 3D


PARAMETERS

-i , -info , -? , /? , /info , /usage : Print this README into console
-inputFolder STRING : Folder from where the input streaks are fetched (on .float format, see FORMATS at bottom of the file). The name format is slice_<num_laser>_<num_ystreak>.float . The num_laser refers of the index of the wall laser which this streak uses. The num_ystreak refers to the y streak. If only the center streak is used (For only one spatial dimension cases) num_ystreak will be equal to the streak height / 2
-fov NDECIMAL : Field of view of the camera, on degrees
-fovrad NDECIMAL : Field of view of the camera, on radians
-voxelRes NINTEGER : Amount of voxels per dimension in the reconstructed volume. The total number of voxels will be NINTEGER^3
-errorThreshold NDECIMAL : The maximum approximation error of each ellipsoid, in voxel sizes. A threshold of 1 will define the maximum error as the reconstruction voxel size
-verbose : Print text during the execution
-renderBatchSize NINTEGER : Maximum amount of ellipsoids that can be drawn in each draw call. Some GPUs crash if a high number is used. In practice, this value is best left as default (160000) unless the program crashes with your GPU
-sphereMaxRec NINTEGER: Maximum number of recursions that each ellipsoid approximation can reach. This value is best left as default.
-memSave : Instead of storing all streaks in memory, the program loads them twice but only stores one of them in memory at once. The I/O cost is duplicated but the memory cost is significantly lowered. Best used on low RAM computers
-ortho NDECIMAL NDECIMAL NDECIMAL NDECIMAL : Defines the bounding box of the reconstruction volume. The first three values are the offsets x, y and z respectively. The fourth value is the bounding box size.
-cam NDECIMAL NDECIMAL NDECIMAL : Defines the camera x, y and z position, respectively
-lookTo NDECIMAL NDECIMAL NDECIMAL : Defines the coordinates of the point to which the camera is focused (x, y and z, respectively)
-laserOrigin NDECIMAL NDECIMAL NDECIMAL : Defines the laser origin x, y and z coordinates, respectively
-t0 NDECIMAL : Defines the initial time for the streaks, by default 0
-lasers [NDECIMAL NDECIMAL NDECIMAL ]+ : Defines all laser wall positions of the scene. Each argument triplet will refer to the x, y and z coordinates of a new laser wall position.
-lasersFile STRING : Defines the route of a file containing all the laser wall positions of the scene. The format is .lasers and its detailed on FORMATS section
-saveFolder STRING : Defines the folder to where all desired data will be dumped
-save2D : Saves a 2D image of the reconstruction and a 2D depth image, using a GPU Laplacian filter (considers only inmediate neighbours, fast), PNG format
-grayscale : If enabled, all images will be generated on grayscale instead of on jet color scale
-save2Dcpu : Saves a 2D image of the reconstruction and a 2D depth image, using a CPU Laplacian filter (considers all neighbours including diagonals, slow), PNG format
-save3D : Saves a 3D volume of the reconstruction using a CPU Laplacian filter, .dump format (see FORMATS)
-save3Draw : Saves a 3D volume of the reconstruction, unfiltered, .dump format (see FORMATS)
-save2Draw : Saves a 2D image of the reconstruction, unfiltered, PNG format
-filename STRING, -filename2d STRING : Name of the filtered 2D image to save
-filename2dRaw STRING : Name of the unfiltered 2D image to save
-filename3d STRING : Name of the filtered 3d dump to save
-filename3dRaw STRING : Name of the unfiltered 3d dump to save
-wallNormal NDECIMAL NDECIMAL NDECIMAL: Normal of the wall, on normalized x, y and z coordinates respectively
-wallDir NDECIMAL NDECIMAL NDECIMAL : Direction of the wall on normalized x, y and z coordinates respectively
-maxIntensity NINTEGER : Best left as default unless really needed. The intensity of all ellipsoids is discretized on a range 0..NINTEGER, by default 255. Can be reduced if the number of ellipsoids is very big and there is risk of integer overflow at some reconstruction voxel intensity
-infoFile STRING : Name of the file to which the program info will be printed (time of execution, ellipsoids uploaded, etc.)
-ellipsoidsPerPixel NINTEGER : Extends NINTEGER ellipsoids per input pixels and distributes them randomly through the temporal domain. Useful when using input images with low temporal resolution
-streakYratio NDECIMAL : Pixel aspect ratio of the input streak images. 
-stochastic : Distributes the ellipsoids randomly over the temporal domain. Reduces artifacts caused by the discretization but raises the error slightly
-ellipsoidsPerPixel NDECIMAL : Extends NDECIMAL ellipsoids in the temporal domain by reconstruction voxel size. By default, 1. Calculates dinamically ellipsoidsPerPixel based on voxelSize and deltaTime 

DATA FORMATS

By default, all binary data formats are on little endian

.float : Format contaning the streak images. Binary.
	 INTEGER : Width of the image (temporal dimension)
	 INTEGER : Height of the image (spatial dimension)
	 INTEGER : Number of channels per image pixel (only the channel 0 is used per now but could be changed)
	 FLOAT, FLOAT, ... : Intensity values of each channel of the image. Order: CHANNELS,WIDTH,HEIGHT (the channels for a width, height are stored collindant in memory)

.dump : 3D reconstruction dump. Binary
	INTEGER: abs(INTEGER) = Size of each dimension of the dump. Total size is abs(INTEGER)^3
	SIZE>0 -> DOUBLE, DOUBLE, .. : Normalized value of each intensity of each voxel, XYZ order
	SIZE<0 -> FLOAT, FLOAT, .. : Normalized value of each intensity of each voxel, XYZ order

.lasers : Format containing a set of scene wall laser hits. Binary
	  FLOAT, FLOAT, FLOAT, : Wall laser 1, with coords x, y and z, respectively
	  FLOAT, FLOAT, FLOAT, : Wall laser 2, with coords x, y and z, respectively
	  ...

