package com.biemme.iodemo;

import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends Activity implements OnClickListener, OnSeekBarChangeListener {
	private long calls = 0;
	private int pins = 4;
	private long fid = 0;
	//timer used for reading
    private Timer t;
    //timer used for writing
    private Timer tW;
    
    private ToggleButton [] btn;
    
    private ProgressBar prg1;
    private SeekBar skb1;
    private TextView temp;
    private ImageView [] inp;
    //sound
    private Button btnSound;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d("msg","onCreate");
		setContentView(R.layout.activity_main);
		setTitle("Biemme Android-Arduino via rs485, modbus");
		
		//get graphical object reference and set to private class' attributes
		btn = new ToggleButton[4];
		inp = new ImageView[4];
		temp = (TextView) findViewById(R.id.temperature);
		
		
		btn[0] = (ToggleButton) findViewById(R.id.toggleButton1);
		btn[1] = (ToggleButton) findViewById(R.id.toggleButton2);
		btn[2] = (ToggleButton) findViewById(R.id.toggleButton3);
		btn[3] = (ToggleButton) findViewById(R.id.toggleButton4);
		
		for (int i=0; i < pins; i++){
			btn[i].setOnClickListener(this);
		}		
		prg1 = (ProgressBar) findViewById(R.id.progressBar1);
		skb1 = (SeekBar) findViewById(R.id.seekBar1);
		skb1.setOnSeekBarChangeListener(this);
		
		inp[0] = (ImageView) findViewById(R.id.imageView1);
		inp[1] = (ImageView) findViewById(R.id.imageView2);
		inp[2] = (ImageView) findViewById(R.id.imageView3);
		inp[3] = (ImageView) findViewById(R.id.imageView4);
	}
	
	@Override
    protected void onResume() {
        super.onResume();
        Log.d("msg","onResume");
        // TODO Auto-generated method stub
        if (fid == 0){
                fid = ModbusLib.openCom();
        }
        stopUpdates();
		startRefreshValues(0,0);
    }
	
	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
		Log.d("msg","onStop");
		stopUpdates();
		if (fid != 0){
            ModbusLib.closeCom((int)fid);
    }
	}
	private void stopUpdates(){
		if (t!=null){
			t.cancel();
			t.purge();
			t=null;
		}
	}
	private void changeButtonsState(boolean value){
		for(int i=0; i < 4; i++){
			btn[i].setEnabled(value);
		}
	}
	
	
	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		int val = -1;
		int addr = -1;
		stopUpdates();
		changeButtonsState(false);
		
		switch(v.getId()){

			case R.id.toggleButton1:
				val = btn[0].isChecked() ? 1 : 0;
				addr = 6;
				break;
			case R.id.toggleButton2:
				val = btn[1].isChecked() ? 1 : 0;
				addr = 7;
				break;
			case R.id.toggleButton3:
				val = btn[2].isChecked() ? 1 : 0;
				addr = 8;
				break;
			case R.id.toggleButton4:
				val = btn[3].isChecked() ? 1 : 0;
				addr = 9;
				break;
		}
		if (val!=-1){
			DelayedWrites(addr, val, 0);						
		}
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	private void startRefreshValues(final int valoreTasto, final long delay){
		if (t!=null){
			t.cancel();
			t.purge();
		}
		TimerTask tt=new TimerTask() {
			
			public void run() {
	            runOnUiThread(new Runnable() {
	                public void run() {
	                	new updateView().execute(valoreTasto);
	                }
	            });
	        }
		};
		t=new Timer();		
		t.scheduleAtFixedRate(tt, delay, 500);
				
	}
	
	
	private class updateView extends AsyncTask<Integer, Integer, int[]>{

		private int[] holdingRegs;
		@Override
		protected void onPostExecute(int[] result) {
			if (result!=null){
				
				for (int i=0; i < 4; i++){
					if (btn[i].isChecked() && result[i+6]==0){
						btn[i].setChecked(false);
					}else if (!btn[i].isChecked() && result[i+6]==1){
						btn[i].setChecked(true);
					}
					
				}
				//Analog Input
				prg1.setIndeterminate(false);
				prg1.setProgress(result[0]);
				
				double temperatureC = (double)holdingRegs[1];
				temperatureC = (((temperatureC*5.0)/1024.0)-0.5)*100; 
						

                DecimalFormat df = new DecimalFormat("#.00");
				temp.setText(String.valueOf(df.format(temperatureC)+" °C"));
				
				//Digital Input
				for (int i=0; i < 4; i++){
					if (result[i+2]==1){
						inp[i].setImageResource(R.drawable.yellow_bulb);
					}else{
						inp[i].setImageResource(R.drawable.red_bulb);
					}
				}
				//PWM
				skb1.setProgress(result[9]);
					
			}
		}
		@Override
		protected int[] doInBackground(Integer... params) {

			try{
					int retries = 0;
					int no_of_registers; 
					int node_to_write = 1;
					long bytes_received = 0;
					int starting_address = 0;
					holdingRegs = new int[35];
					
					no_of_registers = 10;
					
					do{
						bytes_received = ModbusLib.ReadHoldingRegisters((int)fid,node_to_write,starting_address,no_of_registers, holdingRegs);
						
						if (bytes_received>7){
							String s="("+String.valueOf(bytes_received) + ")";
	    					for (int i=0; i<no_of_registers; i++){
	    						s+=String.valueOf(holdingRegs[i]);
	    						s+=",";
	    					}
	    					Log.d("modbus3", s);
							return holdingRegs;
						}
						retries++;
					}while(bytes_received>0 || retries<5);
					

    		}catch(Throwable t){
    			Log.d("modbusERR", t.toString());
    		}
			return null;
		}		
	}
	private void DelayedWrites(final int address, final int value, final long delay){
		if (tW!=null){
			tW.cancel();
			tW.purge();
		}
		TimerTask tt=new TimerTask() {
			
			public void run() {
	            runOnUiThread(new Runnable() {
	                public void run() {
	                	//execute background modbus writes
	                	new WriteRegisters().execute(address, value);
	                }
	            });
	        }
		};
		tW=new Timer();
		tW.schedule(tt, delay);
	}
	private class WriteRegisters extends AsyncTask<Integer, Void, int[]>{
		private int[] holdingRegs;
		@Override
		protected void onPostExecute(int[] result) {
			if (result!=null){
				//if the write give no error, enable the buttons
				changeButtonsState(true);
				startRefreshValues(0,900);
			}
		}
		
		@Override
		protected int[] doInBackground(Integer... params) {
			// TODO Auto-generated method stub
			
			try{
					
					int no_of_registers = 1; 
					int node_to_write = 1;
					long bytes_received = 0;
					int starting_address = 1;
					holdingRegs = new int[35];
					int retries = 0;
					
					starting_address = params[0];		//address of the register to write
					holdingRegs[0]=params[1];			//value to write
					
					
					do{
						bytes_received = ModbusLib.WriteMultipleRegisters((int)fid, node_to_write, starting_address, no_of_registers, holdingRegs);
						retries++;
						
						if (bytes_received > 11){
							String s="("+String.valueOf(bytes_received) + ")";
							s+="["+ String.valueOf(calls++) +"]";
	    					for (int i=0; i<bytes_received; i++){
	    						s+=String.valueOf(holdingRegs[i]);
	    						s+=",";
	    					}
	    					Log.d("modbus16", s);
	    					return holdingRegs;
						}
					}while (bytes_received<=11 || retries<5);
					return null;
    		}catch(Throwable t){
    			Log.d("modbusERR", t.toString());
    		}
			return null;
		}

			
		
		
	}
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
	}


	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
	}


	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		DelayedWrites(9, seekBar.getProgress(), 0);
	}
	

}
