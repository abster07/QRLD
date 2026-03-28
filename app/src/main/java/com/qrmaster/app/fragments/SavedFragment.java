// SavedFragment.java - Fixed
package com.qrmaster.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.qrmaster.app.R;
import com.qrmaster.app.adapters.QRAdapter;
import com.qrmaster.app.models.QRItem;
import com.qrmaster.app.viewmodels.QRViewModel;
import java.util.ArrayList;
import java.util.List;

public class SavedFragment extends Fragment {
    private RecyclerView recyclerView;
    private TextView emptyView;
    private MaterialToolbar toolbar;
    private QRAdapter adapter;
    private QRViewModel viewModel;
    private ActionMode actionMode;
    private List<QRItem> selectedItems = new ArrayList<>();

    // BUG FIX #3: keep a local snapshot of saved items so showDeleteAllDialog()
    // doesn't need to add a new observer (which stacked up and fired repeatedly).
    private List<QRItem> currentSavedItems = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_saved, container, false);

        recyclerView = view.findViewById(R.id.recycler_view);
        emptyView    = view.findViewById(R.id.empty_view);
        toolbar      = view.findViewById(R.id.toolbar);

        setupToolbar();

        viewModel = new ViewModelProvider(this).get(QRViewModel.class);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new QRAdapter(requireContext(), viewModel, new QRAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(QRItem item) {
                if (actionMode != null) {
                    toggleSelection(item);
                } else {
                    adapter.showDetailDialog(item, requireActivity());
                }
            }

            @Override
            public void onItemLongClick(QRItem item) {
                if (actionMode == null) {
                    actionMode = ((AppCompatActivity) requireActivity())
                            .startSupportActionMode(actionModeCallback);
                }
                toggleSelection(item);
            }

            @Override
            public void onMenuClick(QRItem item) {
                showItemMenu(item);
            }
        });
        recyclerView.setAdapter(adapter);

        // BUG FIX #3: cache the latest list so delete-all can use it directly
        viewModel.getSavedItems().observe(getViewLifecycleOwner(), items -> {
            currentSavedItems = items != null ? items : new ArrayList<>();
            adapter.setItems(currentSavedItems);

            if (currentSavedItems.isEmpty()) {
                emptyView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                emptyView.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        });

        return view;
    }

    private void setupToolbar() {
        toolbar.setTitle("Saved");
        toolbar.inflateMenu(R.menu.saved_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_delete_all) {
                showDeleteAllDialog();
                return true;
            } else if (id == R.id.action_select_all) {
                if (actionMode == null) {
                    actionMode = ((AppCompatActivity) requireActivity())
                            .startSupportActionMode(actionModeCallback);
                }
                selectAll();
                return true;
            }
            return false;
        });
    }

    private void toggleSelection(QRItem item) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item);
        } else {
            selectedItems.add(item);
        }
        adapter.setSelectedItems(selectedItems);

        if (actionMode != null) {
            if (selectedItems.isEmpty()) {
                actionMode.finish();
            } else {
                actionMode.setTitle(selectedItems.size() + " selected");
            }
        }
    }

    private void selectAll() {
        selectedItems.clear();
        selectedItems.addAll(currentSavedItems);   // use cached list — no new observer
        adapter.setSelectedItems(selectedItems);
        if (actionMode != null) {
            actionMode.setTitle(selectedItems.size() + " selected");
        }
    }

    private void showItemMenu(QRItem item) {
        String[] options = {"Delete", "Remove from Saved", "Share", "Edit"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Options")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: deleteItem(item);          break;
                        case 1: removeFromSaved(item);     break;
                        case 2: adapter.shareQRCode(item, requireContext()); break;
                        case 3:
                            Toast.makeText(requireContext(), "Edit coming soon",
                                    Toast.LENGTH_SHORT).show();
                            break;
                    }
                })
                .show();
    }

    private void deleteItem(QRItem item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete QR Code")
                .setMessage("Are you sure you want to delete this QR code?")
                .setPositiveButton("Delete", (d, w) -> {
                    viewModel.delete(item);
                    Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeFromSaved(QRItem item) {
        item.setSaved(false);
        viewModel.update(item);
        Toast.makeText(requireContext(), "Removed from saved", Toast.LENGTH_SHORT).show();
    }

    /**
     * BUG FIX #3: The original code called getSavedItems().observe() inside the
     * positive-button click handler. Every time the user opened and confirmed the
     * dialog a NEW observer was registered, so on the second confirmation the
     * delete would fire twice, three times on the third, etc.
     *
     * Fix: use the already-cached `currentSavedItems` list instead.
     */
    private void showDeleteAllDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete All Saved")
                .setMessage("Are you sure you want to delete all saved QR codes?")
                .setPositiveButton("Delete All", (dialog, which) -> {
                    List<Integer> ids = new ArrayList<>();
                    for (QRItem item : currentSavedItems) {
                        ids.add(item.getId());
                    }
                    if (!ids.isEmpty()) {
                        viewModel.deleteMultiple(ids);
                        Toast.makeText(requireContext(), "All saved items deleted",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteSelectedItems() {
        List<Integer> ids = new ArrayList<>();
        for (QRItem item : selectedItems) {
            ids.add(item.getId());
        }
        viewModel.deleteMultiple(ids);
        Toast.makeText(requireContext(),
                selectedItems.size() + " items deleted", Toast.LENGTH_SHORT).show();

        selectedItems.clear();
        adapter.setSelectedItems(selectedItems);
        if (actionMode != null) actionMode.finish();
    }

    private final ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.action_mode_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() == R.id.action_delete) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Delete Selected")
                        .setMessage("Delete " + selectedItems.size() + " items?")
                        .setPositiveButton("Delete", (d, w) -> deleteSelectedItems())
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
            selectedItems.clear();
            adapter.setSelectedItems(selectedItems);
        }
    };
}