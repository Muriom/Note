package com.sxyin.notes.activities;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.sxyin.notes.R;
import com.sxyin.notes.adapters.NotesAdapter;
import com.sxyin.notes.database.NotesDatabase;
import com.sxyin.notes.entities.Note;
import com.sxyin.notes.listeners.NotesListener;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements NotesListener {

    public static final int REQUEST_CODE_ADD_NOTE = 1;
    public static final int REQUEST_CODE_UPDATE_NOTE = 2;
    public static final int REQUEST_CODE_SHOW_NOTES = 3;
    public static final int REQUEST_CODE_SELECT_IMAGE = 4;
    public static final int REQUEST_CODE_STORAGE_PERMISSION = 5;

    private RecyclerView notesRecyclerView;
    private List<Note> noteList;
    private NotesAdapter notesAdapter;

    private int noteClickedPosition = -1;

    private AlertDialog dialogAddURL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageView imageAddNoteMain = findViewById(R.id.imageAddNoteMain);
        imageAddNoteMain.setOnClickListener(v -> startActivityForResult(
                new Intent(getApplicationContext(), CreateNoteActivity.class),
                REQUEST_CODE_ADD_NOTE
        ));

        notesRecyclerView = findViewById(R.id.notesRecyclerView);
        notesRecyclerView.setLayoutManager(
                new StaggeredGridLayoutManager(2,StaggeredGridLayoutManager.VERTICAL)
        );
        noteList = new ArrayList<>();
        notesAdapter = new NotesAdapter(noteList, this);
        notesRecyclerView.setAdapter(notesAdapter);

        getNotes(REQUEST_CODE_SHOW_NOTES,false); //展示所有数据库中的笔记，所以传false

        EditText inputSearch = findViewById(R.id.inputSearch);
        inputSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                notesAdapter.cancelTimer();

            }

            @Override
            public void afterTextChanged(Editable s) {
                if(noteList.size() != 0){
                    notesAdapter.searchNotes(s.toString());
                }

            }
        });


        findViewById(R.id.imageAddNote).setOnClickListener(v ->{
           startActivityForResult(
                   new Intent(getApplicationContext(), CreateNoteActivity.class),
                   REQUEST_CODE_ADD_NOTE
            );
        });

        findViewById(R.id.imageAddImage).setOnClickListener(v ->{
             if(ContextCompat.checkSelfPermission(
                     getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE
             ) != PackageManager.PERMISSION_GRANTED){
                 ActivityCompat.requestPermissions(
                         MainActivity.this,
                         new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                         REQUEST_CODE_STORAGE_PERMISSION
                 );
             }else{
                 selectImage();
             }
        });

        findViewById(R.id.imageAddWebLink).setOnClickListener(v ->{
            showAddURLDialog();
        });
    }
    private void selectImage(){
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        if(intent.resolveActivity(getPackageManager())!=null){
        startActivityForResult(intent,REQUEST_CODE_SELECT_IMAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CODE_STORAGE_PERMISSION && grantResults.length > 0){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                selectImage();
            } else {
                Toast.makeText(this, "Permission Denied!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getPathFromUri(Uri contentUri){
        String filePath;
        Cursor cursor = getContentResolver()
                .query(contentUri, null,null,null,null);
        if(cursor == null){
            filePath = contentUri.getPath();
        }else {
            cursor.moveToFirst();
            int index = cursor.getColumnIndex("_data");
            filePath = cursor.getString(index);
            cursor.close();
        }
        return filePath;
    }

    @Override
    public void onNoteClicked(Note note, int position) {
        noteClickedPosition = position;
        Intent intent = new Intent(getApplicationContext(), CreateNoteActivity.class);
        intent.putExtra("isViewOrUpdate",true);
        intent.putExtra("note", note);
        startActivityForResult(intent, REQUEST_CODE_UPDATE_NOTE);
    }

    private void getNotes(final int requestCode, final boolean isNoteDeleted){

        @SuppressLint("StaticFieldLeak")
        class GetNotesTask extends AsyncTask<Void, Void, List<Note>>{
            @Override
            protected List<Note> doInBackground(Void... voids) {
                return NotesDatabase
                        .getNotesDatabase(getApplicationContext())
                        .noteDao().getAllNotes();
            }

            @SuppressLint("NotifyDataSetChanged")
            @Override
            protected void onPostExecute(List<Note> notes) {
                super.onPostExecute(notes);
                if (requestCode == REQUEST_CODE_SHOW_NOTES) {
                    noteList.addAll(notes);
                    notesAdapter.notifyDataSetChanged();
                } else if (requestCode == REQUEST_CODE_ADD_NOTE) {
                    noteList.add(0, notes.get(0));
                    notesAdapter.notifyItemInserted(0);
                } else if (requestCode == REQUEST_CODE_UPDATE_NOTE) {
                    noteList.remove(noteClickedPosition);//1.先从noteList移除
                    if(isNoteDeleted){//检查是否点击了删除
                        notesAdapter.notifyItemRemoved(noteClickedPosition);//如果点击了删除，则notify Adapter该笔记item被删除
                    } else {//没删除只是进行了更新，则把新加的内容加到和刚才remove相同position的地方，并且notify Adapter有笔记item被改变
                        noteList.add(noteClickedPosition, notes.get(noteClickedPosition));
                        notesAdapter.notifyItemChanged(noteClickedPosition);

                    }
                }

            }
        }
        new GetNotesTask().execute();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_CODE_ADD_NOTE && resultCode == RESULT_OK){
            getNotes(REQUEST_CODE_ADD_NOTE,false); // 表示我们已经添加了一个新笔记到数据库中，所以已删除传false
        } else if (requestCode == REQUEST_CODE_UPDATE_NOTE && resultCode == RESULT_OK) {
            if(data != null ){
                getNotes(REQUEST_CODE_UPDATE_NOTE,data.getBooleanExtra("isNoteDeleted",false));
                // 表示我们已经更新了一个笔记，有可能删除也有可能没删，所以传一个来自CreateNoteActivity到数值，从intent中获取是否删除信息
            }

        } else if(requestCode == REQUEST_CODE_SELECT_IMAGE && resultCode == RESULT_OK){
            if(data != null){
                Uri selectedImageUri = data.getData();
                if(selectedImageUri != null){
                    try {
                        String selectedImagePath = getPathFromUri(selectedImageUri);
                        Intent intent = new Intent(getApplicationContext(), CreateNoteActivity.class);
                        intent.putExtra("isFromQuickActions", true);
                        intent.putExtra("quickActionType","image");
                        intent.putExtra("imagePath",selectedImagePath);
                        startActivityForResult(intent, REQUEST_CODE_ADD_NOTE);
                    } catch (Exception exception){
                        Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }
    private void showAddURLDialog(){
        if(dialogAddURL == null){
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            View view = LayoutInflater.from(this).inflate(
                    R.layout.layout_add_url,
                    (ViewGroup) findViewById(R.id.layoutAddUrlContainer)
            );
            builder.setView(view);

            dialogAddURL = builder.create();
            if (dialogAddURL.getWindow() != null){
                dialogAddURL.getWindow().setBackgroundDrawable(new ColorDrawable(0));
            }
            final EditText inputURL = view.findViewById(R.id.inputURL);
            inputURL.requestFocus();

            view.findViewById(R.id.textAdd).setOnClickListener(v ->{
                if(inputURL.getText().toString().trim().isEmpty()){
                    Toast.makeText(MainActivity.this, "Enter URL", Toast.LENGTH_SHORT).show();
                } else if(!Patterns.WEB_URL.matcher(inputURL.getText().toString()).matches()){
                    Toast.makeText(MainActivity.this, "Enter valid URL", Toast.LENGTH_SHORT).show();
                } else {
                    dialogAddURL.dismiss();
                    Intent intent = new Intent(getApplicationContext(), CreateNoteActivity.class);
                    intent.putExtra("isFromQuickActions", true);
                    intent.putExtra("quickActionType","URL");
                    intent.putExtra("imagePath",inputURL.getText().toString());
                    startActivityForResult(intent, REQUEST_CODE_ADD_NOTE);

                }
            });

            view.findViewById(R.id.textCancel).setOnClickListener(v -> {
                dialogAddURL.dismiss();
            });
        }
        dialogAddURL.show();
    }
}