package io.timelimit.android.ui.util

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.databinding.ConfirmDeleteDialogBinding

abstract class ConfirmDeleteDialogFragment: BottomSheetDialogFragment(), ConfirmDeleteDialogFragmentHandlers {
    lateinit var binding: ConfirmDeleteDialogBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = ConfirmDeleteDialogBinding.inflate(inflater, container, false)

        binding.handlers = this

        return binding.root
    }

    abstract override fun onConfirmDeletion()
}

interface ConfirmDeleteDialogFragmentHandlers {
    fun onConfirmDeletion()
}
