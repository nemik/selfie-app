package net.nemik.mirrorself;

import android.content.Context;
import android.provider.Settings.Secure;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.RectF;
import android.hardware.Camera;
import android.media.FaceDetector;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.Menu;
import android.view.SurfaceView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.loopj.android.http.*;



public class MainActivity extends Activity
{

    private String android_id;
    private static final int MAX_FACES = 1;
	public final static int CAMERA_RETURN_CODE = 1410;
	//private FaceImageView fiw;
    private Camera cam;
    int intPicTaken = 0;
    private Bitmap image;
	private SurfaceView cim;
    private Context mcontext;
    private Canvas cc;

    private int numfaces = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		//fiw = (FaceImageView) findViewById(R.id.facedet);
		
		//Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
		//startActivityForResult(cameraIntent, CAMERA_RETURN_CODE);

        getWindow().setFormat(PixelFormat.TRANSLUCENT);

        cam = Camera.open(0);

        cim = (SurfaceView)findViewById(R.id.facedet);


        try {
            cam.setPreviewDisplay(cim.getHolder());
        } catch (IOException e) {
            e.printStackTrace();
        }

        cam.setPreviewCallback(prevCallBack);
        cam.startPreview();
        Log.d("facedetect","did a start preview");
        mcontext = this;
        cc = new Canvas();

        android_id = Secure.getString(this.getContentResolver(), Secure.ANDROID_ID);

    }


    public Camera.AutoFocusCallback aFCallBack = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean n, Camera camera) {
            try {
                cam.setPreviewDisplay(cim.getHolder());
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.d("facedetect","AUTOFOCUSED NOW TAKING PIC");
            takePicture();
        }
    };

    public Camera.PreviewCallback prevCallBack = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            intPicTaken++;
            try {
                cam.setPreviewDisplay(cim.getHolder());
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if(intPicTaken == 10) {
                    //doTakePicture();
                    cam.autoFocus(aFCallBack);
                    //takePicture();
                }
            } catch (Exception e) {
                System.out.println("onPreviewFrame: " + e.toString());
            }
            //Log.d("facedetect","doing onPreviewCallback");
        }
    };

    // take the picture
    public void doTakePicture() {
        try {

            cam.stopPreview();
            cam.takePicture(null, null, mPicture, mPicture);
        } catch(Exception e){
            System.out.println("doTakePicture: " + e.toString());
        }
    }

    public Camera.PictureCallback mPicture = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            System.out.println("PictureCallback onPictureTaken");
            try {

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                image = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                Bitmap tmpBmp = image.copy(Bitmap.Config.RGB_565, true);

                cc.drawColor(Color.BLACK);
                cc.drawBitmap(image,0,0,null);
                //cim.draw(cc);

                FaceDetector faceDet = new FaceDetector(tmpBmp.getWidth(), tmpBmp.getHeight(), MAX_FACES);
                //FaceDetector faceDet = new FaceDetector(800, 800, MAX_FACES);
                FaceDetector.Face[] faceList = new FaceDetector.Face[MAX_FACES];

                faceDet.findFaces(tmpBmp, faceList);

                // Log the result
                for (int i=0; i < faceList.length; i++) {
                    FaceDetector.Face face = faceList[i];
                    Log.d("FaceDet", "Face [" + face + "]");
                    if (face != null) {
                        Log.d("FaceDet", "Face ["+i+"] - Confidence ["+face.confidence()+"]");
                        PointF pf = new PointF();
                        face.getMidPoint(pf);
                        Log.d("FaceDet", "\t Eyes distance ["+face.eyesDistance()+"] - Face midpoint ["+pf+"]");
                        RectF r = new RectF();
                        r.left = pf.x - face.eyesDistance() / 2;
                        r.right = pf.x + face.eyesDistance() / 2;
                        r.top = pf.y - face.eyesDistance() / 2;
                        r.bottom = pf.y + face.eyesDistance() / 2;
                        //rects[i] = r;
                        numfaces++;
                    }
                    else
                    {
                        numfaces = 0;
                    }
                }


                Toast.makeText(mcontext, numfaces+" FACES",Toast.LENGTH_SHORT).show();

                if(numfaces ==3)
                {
                    Toast.makeText(mcontext, "USING THIS PHOTO!",Toast.LENGTH_SHORT).show();
                    numfaces = 0;

                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    tmpBmp.compress(Bitmap.CompressFormat.JPEG, 90, out);

                    AsyncHttpClient client = new AsyncHttpClient();
                    RequestParams params = new RequestParams();
                    params.put("file", new ByteArrayInputStream(out.toByteArray()),
                            android_id+"-"+(System.currentTimeMillis()/1000)+".jpg", "image/jpeg");

                    client.post("http://apt.nemik.net/mirror-self/pupload.php", params, new AsyncHttpResponseHandler() {
                        @Override
                        public void onSuccess(String response) {
                            //System.out.println(response);
                            Log.d("facedetect", "UPLOADED IMAGE! " + response);
                            Toast.makeText(mcontext, "UPLOADED! " + response, Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                System.out.println("PictureCallback onPictureTaken done");
                //cam.release();
                intPicTaken = 0;
                //cam.reconnect();

                //saveFile(picture);
            } catch (Exception e) {
                System.out.println("onPictureTaken: " + e.toString());
            }
            //takePicture();
        }
    };

    public void takePicture() {
        TakePictureTask takePictureTask = new TakePictureTask();
        takePictureTask.execute();
    }

    private class TakePictureTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPostExecute(Void result) {
            // This returns the preview back to the live camera feed
            try
            {
                cam.startPreview();
            }
            catch (RuntimeException r)
            {
                //camera was released, so whatever. don't leak exception but it'll happen
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            cam.takePicture(null, null, mPicture);

            // Sleep for however long, you could store this in a variable and
            // have it updated by a menu item which the user selects.
            try {
                Thread.sleep(3000); // 3 second preview
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            return null;
        }

    }

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == CAMERA_RETURN_CODE) {
			Bitmap cameraBmp = (Bitmap) data.getExtras().get("data");
			//fiw.setImageBitmap(cameraBmp);
			//fiw.setImage(cameraBmp);
			//fiw.detectFaces();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

    @Override
    public void onStop()
    {
        super.onStop();
        cam.release();
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        cam.release();
    }
}
