package com.sports.gamedayschedular.ui.referee

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sports.gamedayschedular.data.model.AssignmentPosition
import com.sports.gamedayschedular.data.model.RefereeProfile
import com.sports.gamedayschedular.ui.referee.RefereeSearchFragmentArgs
import com.sports.gamedayschedular.databinding.FragmentRefereeSearchBinding
import kotlinx.coroutines.launch

class RefereeSearchFragment : Fragment() {

    private var _binding: FragmentRefereeSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RefereeSearchViewModel by viewModels()
    private val args: RefereeSearchFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRefereeSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = RefereeAdapter { referee ->
            confirmAssignment(referee)
        }
        binding.recyclerReferees.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerReferees.adapter = adapter

        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
        val orgId = sharedPref.getString("selected_org_id", null)
        if (orgId != null) {
            viewModel.setOrganization(orgId)
        }

        binding.editSearch.addTextChangedListener {
            viewModel.setSearchQuery(it?.toString() ?: "")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.referees.collect { list ->
                    adapter.submitList(list)
                }
            }
        }
    }

    private fun confirmAssignment(referee: RefereeProfile) {
        val orgId = sharedPref().getString("selected_org_id", null) ?: return

        if (args.isCheckIn) {
            viewLifecycleOwner.lifecycleScope.launch {
                val success = viewModel.checkIn(referee, orgId)
                if (success) {
                    val eligibility = viewModel.checkLunchEligibility(referee, orgId)
                    val message = if (eligibility.first) {
                        "${referee.name} Checked In.\nELIGIBLE for Lunch Voucher!"
                    } else {
                        "${referee.name} Checked In.\n${eligibility.second}"
                    }
                    
                    AlertDialog.Builder(requireContext())
                        .setTitle("Check-In Successful")
                        .setMessage(message)
                        .setPositiveButton("OK") { _, _ -> findNavController().popBackStack() }
                        .show()
                } else {
                    Toast.makeText(requireContext(), "Check-in failed.", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        val gameId = args.gameId ?: return
        val positionStr = args.position ?: return
        val position = AssignmentPosition.valueOf(positionStr)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = viewModel.validateAndAssign(referee, gameId, position, orgId)
            
            when (result) {
                is ValidationResult.Success -> {
                    Toast.makeText(requireContext(), "Assignment Accepted", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
                is ValidationResult.Warning -> {
                    AlertDialog.Builder(requireContext())
                        .setMessage(result.message)
                        .setPositiveButton("Continue") { _, _ ->
                            performAssignment(referee, position, orgId, gameId)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                is ValidationResult.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun sharedPref() = requireActivity().getPreferences(Context.MODE_PRIVATE)


    private fun performAssignment(referee: RefereeProfile, position: AssignmentPosition, orgId: String, gameId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = viewModel.validateAndAssign(referee, gameId, position, orgId)
            if (result is ValidationResult.Success) {
                Toast.makeText(requireContext(), "Assignment Accepted", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class RefereeAdapter(private val onClick: (RefereeProfile) -> Unit) :
        RecyclerView.Adapter<RefereeAdapter.ViewHolder>() {

        private var items: List<RefereeProfile> = emptyList()

        fun submitList(newItems: List<RefereeProfile>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.textView.text = item.name
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view.findViewById(android.R.id.text1)
        }
    }
}
