package com.gps2usb;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;

import java.io.File;
import java.util.ArrayList;

public class LogFileSelectionFragment extends DialogFragment {

    private File[] logFiles;
    private Context context;

    public LogFileSelectionFragment(Context context, File[] logFiles) {
        this.context = context;
        this.logFiles = logFiles;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log_file_selection, container, false);

        LinearLayout fileListLayout = view.findViewById(R.id.file_list_layout);
        Button sendButton = view.findViewById(R.id.send_button);
        ArrayList<File> selectedFiles = new ArrayList<>();

        // Dynamically create rows for each log file
        for (File file : logFiles) {
            // Create a horizontal layout for the file entry
            LinearLayout rowLayout = new LinearLayout(context);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);

            // Create a CheckBox for file selection
            CheckBox checkBox = new CheckBox(context);
            checkBox.setText(file.getName());
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedFiles.add(file);
                } else {
                    selectedFiles.remove(file);
                }
            });

            // Create a Delete Button for the file
            Button deleteButton = new Button(context);
            deleteButton.setText("Delete");
            deleteButton.setOnClickListener(v -> {
                if (file.delete()) {
                    Toast.makeText(context, file.getName() + " deleted", Toast.LENGTH_SHORT).show();
                    fileListLayout.removeView(rowLayout); // Remove the row from the layout
                } else {
                    Toast.makeText(context, "Failed to delete " + file.getName(), Toast.LENGTH_SHORT).show();
                }
            });

            // Add the CheckBox and Delete Button to the row layout
            rowLayout.addView(checkBox);
            rowLayout.addView(deleteButton);

            // Add the row layout to the main file list layout
            fileListLayout.addView(rowLayout);
        }

        // Send selected files via email when the "Send" button is clicked
        sendButton.setOnClickListener(v -> {
            if (!selectedFiles.isEmpty()) {
                ArrayList<Uri> fileUris = new ArrayList<>();
                for (File file : selectedFiles) {
                    Uri fileUri = FileProvider.getUriForFile(
                            context,
                            context.getPackageName() + ".provider",
                            file);
                    fileUris.add(fileUri);
                }

                // Create and launch email intent
                Intent emailIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                emailIntent.setType("text/plain");
                emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"jchhabr1@ualberta.ca"});
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "GPS Data COE RPP");
                emailIntent.putExtra(Intent.EXTRA_TEXT, "Please find the attached files for review.");
                emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris);
                emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(emailIntent, "Send email using:"));

                dismiss(); // Close the fragment
            } else {
                Toast.makeText(context, "No files selected for sending", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }
}
