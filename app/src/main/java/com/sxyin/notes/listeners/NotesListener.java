package com.sxyin.notes.listeners;

import com.sxyin.notes.entities.Note;

public interface NotesListener {
    void onNoteClicked(Note note, int position);
}
