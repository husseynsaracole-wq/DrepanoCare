package fr.drepanocare.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class AlarmTime(val id: Int, val time: String, val taken: Boolean = false)
data class Medicine(val id: Int, val name: String, val dose: String, val alarms: List<AlarmTime>)

class MainActivity : ComponentActivity() {
    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createChannel()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { DrepanoCareApp(this) }
    }
    private fun createChannel() {
        val channel = NotificationChannel("medicines", "Rappels médicaments", NotificationManager.IMPORTANCE_HIGH)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

private fun loadMedicines(context: Context): MutableList<Medicine> {
    val raw = context.getSharedPreferences("drepano", Context.MODE_PRIVATE).getString("meds", "[]") ?: "[]"
    val arr = JSONArray(raw)
    val result = mutableListOf<Medicine>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i); val aa = o.getJSONArray("alarms"); val alarms = mutableListOf<AlarmTime>()
        for (j in 0 until aa.length()) { val a=aa.getJSONObject(j); alarms.add(AlarmTime(a.getInt("id"), a.getString("time"), a.optBoolean("taken", false))) }
        result.add(Medicine(o.getInt("id"), o.getString("name"), o.optString("dose"), alarms))
    }
    return result
}
private fun saveMedicines(context: Context, meds: List<Medicine>) {
    val arr = JSONArray()
    meds.forEach { m ->
        val o=JSONObject().put("id",m.id).put("name",m.name).put("dose",m.dose)
        val aa=JSONArray(); m.alarms.forEach { aa.put(JSONObject().put("id",it.id).put("time",it.time).put("taken",it.taken)) }
        o.put("alarms",aa); arr.put(o)
    }
    context.getSharedPreferences("drepano", Context.MODE_PRIVATE).edit().putString("meds",arr.toString()).apply()
    meds.forEach { m -> m.alarms.forEach { scheduleAlarm(context, m, it) } }
}
private fun scheduleAlarm(context: Context, med: Medicine, alarm: AlarmTime) {
    val parts=alarm.time.split(":"); if(parts.size!=2) return
    val hour=parts[0].toIntOrNull() ?: return; val minute=parts[1].toIntOrNull() ?: return
    if(hour !in 0..23 || minute !in 0..59) return
    val intent=Intent(context, MedicationAlarmReceiver::class.java).putExtra("name",med.name).putExtra("dose",med.dose).putExtra("id",med.id*10000+alarm.id)
    val pi=PendingIntent.getBroadcast(context, med.id*10000+alarm.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val cal=Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,hour); set(Calendar.MINUTE,minute); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0); if(timeInMillis<=System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR,1) }
    val am=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    try { am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi) }
    catch (_: SecurityException) { am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi) }
}
class MedicationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name=intent.getStringExtra("name") ?: "Médicament"
        val dose=intent.getStringExtra("dose") ?: ""
        val notification=NotificationCompat.Builder(context,"medicines")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("DrepanoCare – rappel")
            .setContentText("C'est l'heure de prendre : $name ${dose}".trim())
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
        val nm=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
            nm.notify(intent.getIntExtra("id",1),notification)
    }
}
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) { loadMedicines(context).forEach { m -> m.alarms.forEach { scheduleAlarm(context,m,it) } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrepanoCareApp(context: Context) {
    var medicines by remember { mutableStateOf(loadMedicines(context)) }
    var page by remember { mutableStateOf("Accueil") }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Medicine?>(null) }
    var name by remember { mutableStateOf("") }; var dose by remember { mutableStateOf("") }; var times by remember { mutableStateOf(listOf("08:00")) }
    var mood by remember { mutableStateOf("Normal") }; var pain by remember { mutableFloatStateOf(0f) }; var location by remember { mutableStateOf("Aucune") }
    var stress by remember { mutableFloatStateOf(2f) }; var notes by remember { mutableStateOf("") }
    var journal by remember { mutableStateOf(context.getSharedPreferences("drepano", Context.MODE_PRIVATE).getString("journal","") ?: "") }
    fun persist(list: List<Medicine>) {
    medicines = list.toMutableList()
    saveMedicines(context, list)
    }
    val total=medicines.sumOf { it.alarms.size }; val taken=medicines.sumOf { m -> m.alarms.count { it.taken } }
    MaterialTheme(colorScheme=lightColorScheme(primary=androidx.compose.ui.graphics.Color(0xFF087F8C), secondary=androidx.compose.ui.graphics.Color(0xFF356A85))) {
        Scaffold(topBar={ TopAppBar(title={ Column { Text("🩸 DrepanoCare"); Text("Mon suivi au quotidien", style=MaterialTheme.typography.labelSmall) } }) },
            bottomBar={ NavigationBar { listOf("Accueil","Traitements","Journal","Historique").forEach { p ->
                NavigationBarItem(selected=page==p,onClick={page=p},icon={Text(when(p){"Accueil"->"⌂";"Traitements"->"💊";"Journal"->"❤️";else->"📊"})},label={Text(p)})
            } } }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                when(page) {
                    "Accueil" -> {
                        Text("Bonjour 👋",style=MaterialTheme.typography.headlineMedium)
                        Text("Ton espace personnel de suivi")
                        Card { Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("Médicaments aujourd'hui",style=MaterialTheme.typography.titleMedium)
                            Text("$taken / $total prises cochées")
                            LinearProgressIndicator(progress={if(total==0)0f else taken.toFloat()/total}, modifier=Modifier.fillMaxWidth())
                            Button(onClick={page="Traitements"}) { Text("Gérer mes médicaments") }
                        } }
                        Card { Column(Modifier.padding(16.dp)) { Text("Mon état du jour",style=MaterialTheme.typography.titleMedium); Text("Moral : $mood · Douleur : ${pain.toInt()}/10"); Button(onClick={page="Journal"}) { Text("Faire mon bilan") } } }
                        Text("En cas de douleur thoracique, difficulté à respirer ou fièvre, demande rapidement une aide médicale. En urgence en France : 15 ou 112.")
                    }
                    "Traitements" -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) { Text("Mes médicaments",style=MaterialTheme.typography.headlineSmall); Button(onClick={ name="";dose="";times=listOf("08:00");showAdd=true;editing=null }) { Text("+ Ajouter") } }
                        medicines.forEach { med ->
                            Card { Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text(med.name,style=MaterialTheme.typography.titleLarge); if(med.dose.isNotBlank()) Text(med.dose)
                                med.alarms.forEach { alarm ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                                        Text("⏰ ${alarm.time}")
                                        Row { Text("Pris"); Checkbox(checked=alarm.taken,onCheckedChange={v->persist(medicines.map { m->if(m.id==med.id)m.copy(alarms=m.alarms.map { a->if(a.id==alarm.id)a.copy(taken=v) else a }) else m })}) }
                                    }
                                }
                                Row {
                                    TextButton(onClick={editing=med;name=med.name;dose=med.dose;times=med.alarms.map{it.time}}){Text("Modifier")}
                                    TextButton(onClick={persist(medicines.filterNot{it.id==med.id})}){Text("Supprimer")}
                                }
                            } }
                        }
                        if(showAdd || editing!=null) {
                            Card { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text(if(editing==null)"Ajouter un médicament" else "Modifier le médicament",style=MaterialTheme.typography.titleLarge)
                                OutlinedTextField(name,{name=it},label={Text("Nom du médicament")},modifier=Modifier.fillMaxWidth())
                                OutlinedTextField(dose,{dose=it},label={Text("Dosage prescrit (facultatif)")},modifier=Modifier.fillMaxWidth())
                                Text("Heures de prise (format HH:MM)")
                                times.forEachIndexed { index,t ->
                                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                        OutlinedTextField(t,{v->times=times.toMutableList().also{it[index]=v}},label={Text("Heure ${index+1}")},modifier=Modifier.weight(1f))
                                        TextButton(onClick={times=times.filterIndexed{ i,_->i!=index }}){Text("Retirer")}
                                    }
                                }
                                TextButton(onClick={times=times+"08:00"}){Text("+ Ajouter une alarme")}
                                Row {
                                    TextButton(onClick={showAdd=false;editing=null}){Text("Annuler")}
                                    Button(onClick={
                                        val valid=times.isNotEmpty() && times.all { t -> Regex("([01]\\d|2[0-3]):[0-5]\\d").matches(t) }
                                        if(name.isNotBlank() && valid) {
                                            val id=editing?.id ?: ((medicines.maxOfOrNull{it.id} ?: 0)+1)
                                            val old=editing?.alarms ?: emptyList()
                                            val alarms=times.mapIndexed { i,t -> AlarmTime(old.getOrNull(i)?.id ?: (i+1),t,old.getOrNull(i)?.taken ?: false) }
                                            val updated=Medicine(id,name.trim(),dose,alarms)
                                            persist(if(editing==null) medicines+updated else medicines.map{if(it.id==id)updated else it})
                                            showAdd=false;editing=null
                                        }
                                    }){Text("Enregistrer")}
                                }
                            } }
                        }
                        Text("Les horaires et doses doivent correspondre à ton ordonnance. Les rappels utilisent les notifications Android.")
                    }
                    "Journal" -> {
                        Text("Mon journal quotidien",style=MaterialTheme.typography.headlineSmall)
                        Text("Comment te sens-tu mentalement ?")
                        listOf("Très bien","Bien","Normal","Pas très bien","Très mal").forEach { option ->
                            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { RadioButton(selected=mood==option,onClick={mood=option}); Text(option) }
                        }
                        Text("Douleur : ${pain.toInt()}/10")
                        Slider(value=pain,onValueChange={pain=it},valueRange=0f..10f,steps=9)
                        Text("Où as-tu mal ?")
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={expanded=it}) {
                            OutlinedTextField(location,{location=it},readOnly=true,label={Text("Localisation")},modifier=Modifier.menuAnchor().fillMaxWidth())
                            ExposedDropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                                listOf("Aucune","Jambes","Bras","Dos","Ventre","Poitrine","Tête","Plusieurs endroits","Autre").forEach { v -> DropdownMenuItem(text={Text(v)},onClick={location=v;expanded=false}) }
                            }
                        }
                        Text("Stress ressenti : ${stress.toInt()}/5")
                        Slider(value=stress,onValueChange={stress=it},valueRange=0f..5f,steps=4)
                        OutlinedTextField(notes,{notes=it},label={Text("Mes notes")},modifier=Modifier.fillMaxWidth(),minLines=3)
                        Button(onClick={
                            val entry="Moral : $mood | Douleur : ${pain.toInt()}/10 | Zone : $location | Stress : ${stress.toInt()}/5 | Notes : $notes"
                            journal="$entry\\n$journal";context.getSharedPreferences("drepano",Context.MODE_PRIVATE).edit().putString("journal",journal).apply()
                        },modifier=Modifier.fillMaxWidth()){Text("Enregistrer mon bilan")}
                    }
                    else -> {
                        Text("Mon historique",style=MaterialTheme.typography.headlineSmall)
                        if(journal.isBlank()) Text("Aucun bilan enregistré pour le moment.")
                        else Text(journal)
                        Text("Les données sont stockées localement sur cet appareil.")
                    }
                }
            }
        }
    }
}
