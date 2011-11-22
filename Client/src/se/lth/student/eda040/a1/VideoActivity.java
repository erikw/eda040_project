package se.lth.student.eda040.a1;

import se.lth.student.eda040.a1.data.Input;
import se.lth.student.eda040.a1.data.Output;
import se.lth.student.eda040.a1.data.ClientMonitor;
import se.lth.student.eda040.a1.data.ImageFetcher;
import se.lth.student.eda040.a1.data.DisconnectionDetecter;
import se.lth.student.eda040.a1.network.ClientProtocol;
import se.lth.student.eda040.a1.network.Command;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Toast;

import java.net.UnknownHostException;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;


public class VideoActivity extends Activity {

	private Input in0;
	private Input in1;
	private Output out0;
	private Output out1;
	private Handler handler;
	private ClientMonitor monitor;
	private ContextMenu contextMenu;
	private int currentCam = -1;
	String errorKey = "errorKey";

	private String[] cams = {
		"argus-1",
		"argus-2",
		"argus-3",
		"argus-4",
		"argus-5",
		"argus-6",
		"argus-7",
		"argus-8",
		"argus-9",
		"argus-10",
		"fake cam",
	};

	private List<String> cameras = new ArrayList<String>(Arrays.asList(cams));

	private String[] connectedCameras = new String[2];
	private ArrayAdapter<String> adapter;
	private AlertDialog cameraPicker;
	private AwesomeVideoView avv;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.videoview);
		setUpCameraDialog();
		avv = (AwesomeVideoView) findViewById(R.id.avv);
		avv.setVideoActivity(this);
		
		// TODO the socket instantiation is blocking. OK for now but if possible do this in another setup-thread.
		handler = new Handler();
		monitor = new ClientMonitor();
		ImageFetcher fetcher = new ImageFetcher(monitor, avv, handler);
		DisconnectionDetecter detecter = new DisconnectionDetecter(monitor, avv, handler);
		ClientProtocol protocol0 = new ClientProtocol((byte) 0);
		ClientProtocol protocol1 = new ClientProtocol((byte) 1);
		monitor.addProtocol((byte) 0, protocol0);
		monitor.addProtocol((byte) 1, protocol1);
		in0 = new Input(monitor, protocol0);
		in1 = new Input(monitor, protocol1);
		out0 = new Output(monitor, protocol0);
		out1 = new Output(monitor, protocol1);

		in0.start();
		in1.start();
		out0.start();
		out1.start();
		fetcher.start();
		detecter.start();
	}

	private void setUpCameraDialog() {
		cameras = new ArrayList<String>(Arrays.asList(cams));
		adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, cameras);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Pick a camera");
		builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialogInterface, int item) {
				String host = adapter.getItem(item);
				boolean connected = false;
				if (host.equals("fake cam")) {
					connected = connectCamera((byte) currentCam, "10.0.2.2");
				} else {
					connected = connectCamera((byte) currentCam, host +
						".student.lth.se");
				}
				if(connected){
					connectedCameras[currentCam] = host;
					adapter.remove(host);
					adapter.notifyDataSetChanged();
				}
			}
		});
		cameraPicker = builder.create();
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.videomenu, menu);
		return true;
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.connectCam0:
				Log.d("VideoActivity", "Selected connectCam0 option.");
				currentCam = 0;
				cameraPicker.show();
				avv.connect((byte) 0);
				break;
		case R.id.connectCam1:
				Log.d("VideoActivity", "Selected connectCam1 option.");
				currentCam = 1;
				cameraPicker.show();
				avv.connect((byte) 1);
				break;
		case R.id.disconnectCam0:
				Log.d("VideoActivity", "Selected disconnectCam0");
				disconnectCamera((byte) 0);
				avv.disconnect((byte) 0);
				break;
		case R.id.disconnectCam1:
				Log.d("VideoActivity", "Selected disconnectCam1");
				disconnectCamera((byte) 1);
				avv.disconnect((byte) 1);
		case R.id.setIdle:
				monitor.setIdleMode();
				break;
		default:
				super.onOptionsItemSelected(item);
		}
		return true;
	}

    private boolean connectCamera(byte cameraId, String host){
		Bundle dialogArgs = new Bundle();
		boolean connected = false;
		String errorMsg = null;
        try {
			monitor.connectTo(cameraId, host);
			connected = true;
			Log.d("VideoActivity", "Connected to camera: " + cameraId);
        } catch (UnknownHostException e){
			errorMsg = "Failed to connect camera: " + cameraId +
                    ".\nUnable to connect to host: " + host + ".";
        } catch (IOException e){
			errorMsg = "Failed to connect camera: " + cameraId +
				"\n" + e.getMessage();
        } catch (IllegalArgumentException e){
			errorMsg = "Failed to connect camera: " + cameraId + 
                    "Camera has not been set up!";
        }

		if (errorMsg != null) {
            Log.d("VideoActivity", errorMsg);
			dialogArgs.putString(errorKey, errorMsg);
			showDialog(errorKey.hashCode(), dialogArgs);
		}
		return connected;
    }

	/**
	 * Disconnect a camera as well as some magic with the camera list.
	 */
	public void disconnectCamera(byte cameraId) {
		currentCam = cameraId;
		int oppositeCam = currentCam == 0 ? 1 : 0;
		connectedCameras[currentCam] = null;
		setUpCameraDialog();
		adapter.remove(connectedCameras[oppositeCam]);
		adapter.notifyDataSetChanged();
		monitor.gracefullDisconnect((byte) currentCam);
	}

	public void emergencyDisconnenctCamera(byte cameraId) {
		currentCam = cameraId;
		int oppositeCam = currentCam == 0 ? 1 : 0;
		connectedCameras[currentCam] = null;
		setUpCameraDialog();
		adapter.remove(connectedCameras[oppositeCam]);
		adapter.notifyDataSetChanged();
		avv.disconnect(cameraId);
	}

	public boolean onPrepareOptionsMenu(Menu menu) {
				// Note: this overides the icons that otherwise would be used according to the xml.
				menu.clear();	 // Clears all items, below: rebuild from scratch.
				if (monitor.isConnectedCamera((byte) 0)) {
					menu.add(Menu.NONE, R.id.disconnectCam0, 0, "Disconnect c0");
				} else {
					menu.add(Menu.NONE, R.id.connectCam0, 0, "Connect c0");
				}

				if (monitor.isConnectedCamera((byte) 1)) {
					menu.add(Menu.NONE, R.id.disconnectCam1, 1, "Disconnect c1");
				} else {
					menu.add(Menu.NONE, R.id.connectCam1, 1, "Connect c1");
				}
				menu.add(Menu.NONE, R.id.setIdle, 2, "Set Idle");
		return true;
	}

	public Dialog onCreateDialog(int id, Bundle dialogArgs){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
				}
			})
			.setMessage(dialogArgs.getString(errorKey));
		AlertDialog alert = builder.create();
		return alert;
	}
}
