package com.sports.gamedayschedular.ui.transform

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import coil.load
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sports.gamedayschedular.R
import com.sports.gamedayschedular.data.model.*
import com.sports.gamedayschedular.databinding.FragmentTransformBinding
import com.sports.gamedayschedular.databinding.ItemDivisionColumnBinding
import com.sports.gamedayschedular.databinding.ItemGameCardBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TransformFragment : Fragment() {

    private var _binding: FragmentTransformBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransformViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransformBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val divisionAdapter = DivisionAdapter(
            onAssignClick = { gameWithAssignments, position ->
                val action = TransformFragmentDirections.actionNavTransformToNavRefereeSearch(
                    gameId = gameWithAssignments.game.id,
                    position = position.name
                )
                findNavController().navigate(action)
            },
            onRemoveClick = { assignmentWithName, game ->
                showRemoveConfirmation(assignmentWithName, game)
            }
        )
        
        binding.recyclerviewDivisions.layoutManager = 
            LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        binding.recyclerviewDivisions.adapter = divisionAdapter

        val sharedPref = requireActivity().getSharedPreferences("GDS_PREFS", Context.MODE_PRIVATE)
        val orgId = sharedPref.getString("selected_org_id", null)
        val orgName = sharedPref.getString("selected_org_name", null)
        
        if (orgId != null) {
            viewModel.setOrganization(orgId)
            binding.textOrgName.text = orgName?.uppercase() ?: ""
        }

        binding.btnCheckIn.setOnClickListener {
            val action = TransformFragmentDirections.actionNavTransformToNavRefereeSearch(
                gameId = null,
                position = null,
                isCheckIn = true
            )
            findNavController().navigate(action)
        }

        // Fix Clock Update: Must run independently of ViewModel flows
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val time = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date())
                    binding.textClock.text = time
                    delay(1000L)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.divisionsWithGames.collect { list ->
                        divisionAdapter.submitList(list)
                        binding.textEmptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.dashboardSummary.collect { summary ->
                        val code = summary.printerCode
                        binding.textPrinterCode.text = when {
                            code == "PRINTING" -> "PRINTING..."
                            code.startsWith("CONSUMED") || code.isEmpty() -> "--- ---"
                            code.length == 6 -> "${code.substring(0, 3)} ${code.substring(3)}"
                            else -> code
                        }
                        binding.textCheckInSummary.text = "${summary.checkInCount} Referees Checked In"

                        // Apply Dynamic Theme
                        binding.rootLayout.setBackgroundColor(summary.backgroundColor)
                        applyTextColorToAll(summary.textColor)

                        // Load Logo if available
                        if (!summary.logoUrl.isNullOrEmpty()) {
                            binding.imgLogo.visibility = View.VISIBLE
                            binding.imgCalendar.visibility = View.GONE
                            binding.imgLogo.load(summary.logoUrl) {
                                crossfade(true)
                                placeholder(R.drawable.ic_calendar)
                                error(R.drawable.ic_calendar)
                            }
                        } else {
                            binding.imgLogo.visibility = View.GONE
                            binding.imgCalendar.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun applyTextColorToAll(color: Int) {
        binding.textAppTitle.setTextColor(color)
        binding.textOrgName.setTextColor(color)
        binding.textClock.setTextColor(color)
        binding.textAutoReload.setTextColor(color)
        
        // Icon Tinting
        binding.imgCalendar.setColorFilter(color)
        
        val legendContainer = binding.legendBar
        for (i in 0 until legendContainer.childCount) {
            val child = legendContainer.getChildAt(i)
            if (child is ViewGroup) {
                for (j in 0 until child.childCount) {
                    val innerChild = child.getChildAt(j)
                    if (innerChild is TextView) {
                        innerChild.setTextColor(color)
                    }
                }
            }
        }
    }

    private fun showRemoveConfirmation(assignmentWithName: AssignmentWithName, game: Game) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Assignment")
            .setMessage("Are you sure you want to remove ${assignmentWithName.refereeName} from this game?")
            .setPositiveButton("Remove") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val success = viewModel.removeReferee(assignmentWithName.assignment, game.id)
                    if (success) {
                        Toast.makeText(requireContext(), "Referee removed.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to remove referee.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class DivisionAdapter(
        private val onAssignClick: (GameWithAssignments, AssignmentPosition) -> Unit,
        private val onRemoveClick: (AssignmentWithName, Game) -> Unit
    ) : ListAdapter<DivisionWithGames, DivisionAdapter.ViewHolder>(DivisionDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDivisionColumnBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding, onAssignClick, onRemoveClick)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        class ViewHolder(
            private val binding: ItemDivisionColumnBinding,
            private val onAssignClick: (GameWithAssignments, AssignmentPosition) -> Unit,
            private val onRemoveClick: (AssignmentWithName, Game) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: DivisionWithGames) {
                binding.textDivisionName.text = "${item.division.name} ${item.division.gender}".uppercase()
                binding.textHalfDuration.text = "${item.division.halfDurationMinutes} min halves"
                binding.textBallSize.text = "Size ${item.division.ballSize}"
                binding.textPlayerCount.text = "${item.division.playersPerTeam}v${item.division.playersPerTeam}"

                val gameAdapter = GameAdapter(onAssignClick, onRemoveClick)
                binding.recyclerviewGames.adapter = gameAdapter
                gameAdapter.submitList(item.games)
            }
        }
    }

    class GameAdapter(
        private val onAssignClick: (GameWithAssignments, AssignmentPosition) -> Unit,
        private val onRemoveClick: (AssignmentWithName, Game) -> Unit
    ) : ListAdapter<GameWithAssignments, GameAdapter.ViewHolder>(GameDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemGameCardBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding, onAssignClick, onRemoveClick)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        class ViewHolder(
            private val binding: ItemGameCardBinding,
            private val onAssignClick: (GameWithAssignments, AssignmentPosition) -> Unit,
            private val onRemoveClick: (AssignmentWithName, Game) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: GameWithAssignments) {
                val game = item.game
                val assignments = item.assignments

                val df = SimpleDateFormat("h:mm a", Locale.getDefault())
                binding.textGameTime.text = df.format(game.date)
                binding.textGameNumber.text = "#${game.gameNumber}"
                binding.textTeams.text = "${game.homeTeamName} vs ${game.awayTeamName}"
                binding.textField.text = "Field ${game.fieldNumber}"

                val hr = assignments.find { it.assignment.position == AssignmentPosition.HeadReferee }
                val ar1 = assignments.find { it.assignment.position == AssignmentPosition.AssistantReferee }
                val ar2 = assignments.filter { it.assignment.position == AssignmentPosition.AssistantReferee }.getOrNull(1)

                setupSlot(binding.btnAssignHr, hr, AssignmentPosition.HeadReferee, item)
                setupSlot(binding.btnAssignAr1, ar1, AssignmentPosition.AssistantReferee, item)
                setupSlot(binding.btnAssignAr2, ar2, AssignmentPosition.AssistantReferee, item)

                // Fill logic
                val hasHr = hr != null
                val arCount = assignments.count { it.assignment.position == AssignmentPosition.AssistantReferee }
                val allFilled = hasHr && arCount >= 2

                val color = when {
                    allFilled -> Color.parseColor("#4CAF50") // Green
                    hasHr -> Color.parseColor("#FFEB3B") // Yellow
                    else -> Color.parseColor("#F44336") // Red
                }
                
                val bg = binding.cardContainer.background as GradientDrawable
                bg.setColor(color)
                
                // Adjust text contrast based on fill
                val textColor = if (allFilled || !hasHr) Color.WHITE else Color.BLACK
                binding.textTeams.setTextColor(textColor)
                binding.textGameTime.setTextColor(textColor)
                binding.textGameNumber.setTextColor(textColor)

                // Pulsing Logic for Active Games (based on division duration)
                val now = Date()
                val startTime = game.date
                val endTime = Date(startTime.time + (item.durationMinutes.toLong() * 60 * 1000))
                
                if (now.after(startTime) && now.before(endTime)) {
                    val pulse = AnimationUtils.loadAnimation(binding.root.context, R.anim.pulse)
                    binding.root.startAnimation(pulse)
                } else {
                    binding.root.clearAnimation()
                }
            }

            private fun setupSlot(view: TextView, assignment: AssignmentWithName?, position: AssignmentPosition, item: GameWithAssignments) {
                if (assignment != null) {
                    view.text = assignment.refereeName
                    view.alpha = 1.0f
                    
                    if (assignment.assignment.checkedIn) {
                        view.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_person_outline, 0, R.drawable.ic_check_circle, 0
                        )
                    } else {
                        view.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_person_outline, 0, R.drawable.ic_remove_circle, 0
                        )
                    }
                    
                    // Always allow removal, even if checked in
                    view.setOnClickListener { onRemoveClick(assignment, item.game) }
                } else {
                    view.text = if (position == AssignmentPosition.HeadReferee) "Assign Head Ref" else "Assign Assistant Ref"
                    view.alpha = 0.5f
                    view.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_person_outline, 0, 0, 0)
                    view.setOnClickListener { onAssignClick(item, position) }
                }
            }
        }
    }

    class DivisionDiffCallback : DiffUtil.ItemCallback<DivisionWithGames>() {
        override fun areItemsTheSame(oldItem: DivisionWithGames, newItem: DivisionWithGames) =
            oldItem.division.name == newItem.division.name && oldItem.division.gender == newItem.division.gender
        override fun areContentsTheSame(oldItem: DivisionWithGames, newItem: DivisionWithGames) =
            oldItem == newItem
    }

    class GameDiffCallback : DiffUtil.ItemCallback<GameWithAssignments>() {
        override fun areItemsTheSame(oldItem: GameWithAssignments, newItem: GameWithAssignments) =
            oldItem.game.id == newItem.game.id
        override fun areContentsTheSame(oldItem: GameWithAssignments, newItem: GameWithAssignments) =
            oldItem == newItem
    }
}
