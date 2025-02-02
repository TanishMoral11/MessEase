package com.theayushyadav11.messease.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.theayushyadav11.messease.R
import com.theayushyadav11.messease.adapters.DateAdapter
import com.theayushyadav11.messease.adapters.DateItem
import com.theayushyadav11.messease.databinding.DailyMenuBinding
import com.theayushyadav11.messease.databinding.FragmentHomeBinding
import com.theayushyadav11.messease.models.Poll
import com.theayushyadav11.messease.utils.FireBase
import com.theayushyadav11.messease.utils.Mess
import com.theayushyadav11.messease.viewModels.HomeViewModel
import com.theayushyadav11.myapplication.database.MenuDatabase
import com.theayushyadav11.myapplication.models.Menu
import com.theayushyadav11.myapplication.models.Particulars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment(), DateAdapter.Listeners {

    private lateinit var binding: FragmentHomeBinding
    private var pos: Int = 0
    var dayOfWeek = MutableLiveData<Int>()
    private lateinit var mess: Mess
    private lateinit var list: MutableList<Particulars>
    private var day = Date().date
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    lateinit var homeViewModel: HomeViewModel
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
        listeners()
        onData()
        database()
    }

    private fun init() {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        dayOfWeek.value = homeViewModel.dayOfWeek.value
        val rv = binding.rv
        val adapter = DateAdapter(this)
        rv.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rv.adapter = adapter
        homeViewModel.currentDate.observe(viewLifecycleOwner) {
            pos = it
            binding.rv.smoothScrollToPosition(pos + 2)

        }


        homeViewModel.currentMonthYear.observe(viewLifecycleOwner) {
            binding.month.text = it
        }
        mess = Mess(requireContext())


    }

    fun scrollToPosition(direction: Int) {
        val layoutManager = binding.rv.layoutManager as LinearLayoutManager
        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
        val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
        if (firstVisibleItemPosition > 6) {
            binding.imageView2.visibility = View.VISIBLE
        }
        if (lastVisibleItemPosition < 24) {
            binding.imageView.visibility = View.VISIBLE
        }

        if (direction == -1) { // Scroll left
            if (firstVisibleItemPosition > 4) {
                binding.imageView2.visibility = View.VISIBLE
                binding.rv.smoothScrollToPosition(firstVisibleItemPosition - 4)
            } else {
                binding.rv.smoothScrollToPosition(0)
                binding.imageView.visibility = View.INVISIBLE
            }
        } else if (direction == 1) { // Scroll right
            if (lastVisibleItemPosition < 29) {
                binding.rv.smoothScrollToPosition(lastVisibleItemPosition + 4)
                binding.imageView.visibility = View.VISIBLE
            } else {
                binding.imageView2.visibility = View.INVISIBLE
            }
        }
    }

    fun addELements(date: String) {


        homeViewModel.getPolls(date, onSuccess = {
            binding.adder.removeAllViews()
            for (poll in it) {
                addPoll(poll)
            }
            addFood(list)

        }, onFailure = {
            addFood(list)
            mess.log(it)
        })
    }

    fun addFood(list: MutableList<Particulars>) {
        for (i in 0..3) {
            val layout = LayoutInflater.from(requireContext())
                .inflate(R.layout.daily_menu, binding.adder, false)
            val view = DailyMenuBinding.inflate(layoutInflater)
            val foodType = layout.findViewById<TextView>(R.id.foodType)
            val foodMenu = layout.findViewById<TextView>(R.id.foodMenu)
            val foodTimeing = layout.findViewById<TextView>(R.id.foodTimeing)
            foodType.text = list[i].foodType
            foodMenu.text = list[i].food

            foodTimeing.text = list[i].timing

            binding.adder.addView(layout)
        }
    }

    private fun addPoll(poll: Poll) {
        if (!isAdded) return
        val context = context ?: return //
        val itemView = LayoutInflater.from(requireContext())
            .inflate(R.layout.poll_layout, binding.adder, false)
        val question: TextView = itemView.findViewById(R.id.tvQuestion)
        val name: TextView = itemView.findViewById(R.id.tvname)
        val linearLayout: LinearLayout = itemView.findViewById(R.id.radioGroup)
        itemView.findViewById<ImageView>(R.id.delete).isVisible = false
        itemView.findViewById<LinearLayout>(R.id.vv).isVisible = false
        itemView.findViewById<TextView>(R.id.time).text = poll.time

        question.text = poll.question
        name.text = poll.creater
        binding.adder.addView(itemView)
        linearLayout.removeAllViews()


        var listOfRb: MutableList<RadioButton> = mutableListOf()
        val opt = poll.options.toList()
        for (i in 0 until opt.size) {
            val option = opt[i]
            val view = LayoutInflater.from(linearLayout.context)
                .inflate(R.layout.poll_layout_element, linearLayout, false)
            val optTitle = view.findViewById<TextView>(R.id.title)
            val optRb = view.findViewById<RadioButton>(R.id.rb)
            listOfRb.add(optRb)
            val optNop = view.findViewById<TextView>(R.id.nop)
            val pl: ConstraintLayout = view.findViewById(R.id.pl)


            var count = 0
            database.child("pollResult").child(poll.uid)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        count = 0
                        var tv = 0
                        for (child in snapshot.children) {
                            tv++
                            if (child.value.toString() == option)
                                count++
                        }
                        val optPb = view.findViewById<ProgressBar>(R.id.ProgressBar)
                        var progress = 0
                        if (tv > 0) {
                            progress = ((count) * 100) / tv
                        }
                        optTitle.text = option
                        optNop.text = (count).toString()
                        optPb.progress = progress
                    }

                    override fun onCancelled(error: DatabaseError) {

                    }

                })
            database.child("pollResult").child(poll.uid).child(auth.currentUser?.uid.toString())
                .addValueEventListener(
                    object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            mess.log(snapshot)
                            if (snapshot.value.toString().trim() == optTitle.text.trim()) {
                                optRb.isChecked = true

                            } else
                                optRb.isChecked = false
                        }

                        override fun onCancelled(error: DatabaseError) {

                        }

                    }
                )



            pl.setOnClickListener {
                clickedOpt(listOfRb, poll.options, i, poll.uid)

            }
            optRb.setOnClickListener {
                clickedOpt(listOfRb, poll.options, i, poll.uid)

            }

            linearLayout.addView(view)

        }


    }

    private fun clickedOpt(
        listOfRb: MutableList<RadioButton>,
        options: MutableList<String>,
        currentIndex: Int,
        uid: String,
    ) {
        var i = 0
        for (i in 0 until listOfRb.size) {
            if (i == currentIndex) {
                listOfRb[i].isChecked = true
                optionSelect(uid, options[i])

            } else {
                listOfRb[i].isChecked = false

            }
        }

    }


    fun listeners() {

        binding.imageView.setOnClickListener {

            scrollToPosition(-1)
        }
        binding.imageView2.setOnClickListener {

            scrollToPosition(1)
        }
    }

    override fun ondateSelected(
        date: DateItem,
        position: Int,
        main: DateAdapter.DateViewHolder
    ) {
        day = position + 1
        addELements("$day${getCurrentDate()}")
        binding.rv.smoothScrollToPosition(position + 3)
        main.main.setBackgroundColor(resources.getColor(R.color.menu))
        main.dates.setTextColor(resources.getColor(R.color.food))
        dayOfWeek.value = date.weekday


    }

    fun onData() {
        val db = MenuDatabase.getDatabase(requireContext())
        val dao = db.menuDao()
        GlobalScope.launch(Dispatchers.IO) {
            val menu = dao.getMenu()
            Log.d("yadavjikabeta ", menu.toString())
            withContext(Dispatchers.Main) {
                dayOfWeek.observe(viewLifecycleOwner) { value ->
                    list = value?.let { getMenu(menu, it) }!!
                    if (list != null) {
                        addELements("$day${getCurrentDate()}")
                    }
                    Log.d("yadavjikabeta ", list.toString())
                }


            }
//            val list= dayOfWeek.value?.let { getMenu(menu, it) }
//            Log.d("yadavjikabeta ",list.toString())


        }

    }

    fun database() {
        val db = MenuDatabase.getDatabase(requireContext())
        val menuDao = db.menuDao()
        GlobalScope.launch {
            val retrievedMenu = menuDao.getMenu()
            Log.d("Menu", retrievedMenu.toString())
        }

    }

    fun getMenu(menu: Menu, day: Int): MutableList<Particulars> {
        return when (day) {
            Calendar.MONDAY -> menu.menu.list[0].toMutableList()
            Calendar.TUESDAY -> menu.menu.list[1].toMutableList()
            Calendar.WEDNESDAY -> menu.menu.list[2].toMutableList()
            Calendar.THURSDAY -> menu.menu.list[3].toMutableList()
            Calendar.FRIDAY -> menu.menu.list[4].toMutableList()
            Calendar.SATURDAY -> menu.menu.list[5].toMutableList()
            Calendar.SUNDAY -> menu.menu.list[6].toMutableList()


            else -> mutableListOf()
        }
    }

    fun optionSelect(uid: String, option: String) {

        database.child("pollResult").child(uid).child(auth.currentUser?.uid.toString())
            .setValue(option)
        database.child("pollResult").child(uid).child(auth.currentUser?.uid.toString())
            .addValueEventListener(
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.value == null) {
                            FireBase().incrementTotalVotes(uid) { value, e ->
                                mess.toast(value.toString())
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                    }
                })


    }

    fun getCurrentDate(): String {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("/MM/yyyy", Locale.getDefault())
        return dateFormat.format(calendar.time)
    }
}