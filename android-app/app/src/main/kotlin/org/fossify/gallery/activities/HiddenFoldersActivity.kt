package org.fossify.shiguang.activities

import android.os.Bundle
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.shiguang.R
import org.fossify.shiguang.adapters.ManageHiddenFoldersAdapter
import org.fossify.shiguang.databinding.ActivityManageFoldersBinding
import org.fossify.shiguang.extensions.addNoMedia
import org.fossify.shiguang.extensions.config
import org.fossify.shiguang.extensions.getNoMediaFolders

class HiddenFoldersActivity : SimpleActivity(), RefreshRecyclerViewListener {

    private val binding by viewBinding(ActivityManageFoldersBinding::inflate)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateFolders()
        setupOptionsMenu()
        binding.manageFoldersToolbar.title = getString(R.string.hidden_folders)

        setupEdgeToEdge(
            padTopSystem = listOf(binding.manageFoldersAppbar),
            padBottomSystem = listOf(binding.manageFoldersList)
        )
        setupMaterialScrollListener(binding.manageFoldersList, binding.manageFoldersAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.manageFoldersAppbar, NavigationIcon.Arrow)
    }

    private fun updateFolders() {
        getNoMediaFolders {
            runOnUiThread {
                binding.manageFoldersPlaceholder.apply {
                    text = getString(R.string.hidden_folders_placeholder)
                    beVisibleIf(it.isEmpty())
                    setTextColor(getProperTextColor())
                }

                val adapter = ManageHiddenFoldersAdapter(this, it, this, binding.manageFoldersList) {}
                binding.manageFoldersList.adapter = adapter
            }
        }
    }

    private fun setupOptionsMenu() {
        binding.manageFoldersToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_folder -> addFolder()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun refreshItems() {
        updateFolders()
    }

    private fun addFolder() {
        FilePickerDialog(this, config.lastFilepickerPath, false, config.shouldShowHidden, false, true) {
            config.lastFilepickerPath = it
            ensureBackgroundThread {
                addNoMedia(it) {
                    updateFolders()
                }
            }
        }
    }
}
