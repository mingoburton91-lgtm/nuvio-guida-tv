package com.nuvio.tv.ui.screens.tvguide
import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import org.json.*
import org.xmlpull.v1.*
import java.net.*
import java.text.Normalizer
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val ADDON="https://tvvoo.hayd.uk/cfg-it-uk-fr-de-pt-es-al-tr-nl-ar-bk-ru-ro-pl-bg-hc1"
private val countries=listOf("it" to "Italia","uk" to "UK","fr" to "Francia","de" to "Germania","pt" to "Portogallo","es" to "Spagna","al" to "Albania","tr" to "Turchia","nl" to "Paesi Bassi","ar" to "Argentina","bk" to "Balcani","ru" to "Russia","ro" to "Romania","pl" to "Polonia","bg" to "Bulgaria")
private val zone=ZoneId.of("Europe/Rome")
private val tf=DateTimeFormatter.ofPattern("HH:mm",Locale.ITALIAN).withZone(zone)
private data class Programme(val title:String,val start:Instant,val end:Instant)
private data class Channel(val id:String,val name:String,val logo:String,val programmes:List<Programme>)
private data class EpgSource(val name:String,val url:String,val enabled:Boolean=true,val country:String="")
private data class EpgData(val names:Map<String,List<String>>,val programmes:Map<String,List<Programme>>)
private val defaultEpg=mapOf(
"it" to listOf(EpgSource("Italia · Sky","https://iptv-org.github.io/epg/guides/it/guidatv.sky.it.epg.xml",true,"it"),EpgSource("Italia · Mediaset","https://iptv-org.github.io/epg/guides/it/mediaset.it.epg.xml",true,"it")),
"uk" to listOf(EpgSource("UK","https://iptv-org.github.io/epg/guides/uk/ontvtonight.com.epg.xml",true,"uk")),
"fr" to listOf(EpgSource("Francia","https://iptv-org.github.io/epg/guides/fr/programme-tv.net.epg.xml",true,"fr")),
"de" to listOf(EpgSource("Germania","https://iptv-org.github.io/epg/guides/de/hd-plus.de.epg.xml",true,"de")),
"pt" to listOf(EpgSource("Portogallo","https://iptv-org.github.io/epg/guides/pt/meo.pt.epg.xml",true,"pt")),
"es" to listOf(EpgSource("Spagna","https://iptv-org.github.io/epg/guides/es/programacion-tv.elpais.com.epg.xml",true,"es")),
"al" to listOf(EpgSource("Albania","https://iptv-org.github.io/epg/guides/al/ipko.com.epg.xml",true,"al")),
"tr" to listOf(EpgSource("Turchia · TV+","https://iptv-org.github.io/epg/guides/tr/tvplus.com.tr.epg.xml",true,"tr"),EpgSource("Turchia · Digiturk","https://iptv-org.github.io/epg/guides/tr/digiturk.com.tr.epg.xml",true,"tr")),
"nl" to listOf(EpgSource("Paesi Bassi","https://iptv-org.github.io/epg/guides/nl/delta.nl.epg.xml",true,"nl")),
"ar" to listOf(EpgSource("Argentina","https://iptv-org.github.io/epg/guides/ar/mi.tv.epg.xml",true,"ar")),
"ru" to listOf(EpgSource("Russia","https://iptv-org.github.io/epg/guides/ru/tv.yandex.ru.epg.xml",true,"ru")),
"ro" to listOf(EpgSource("Romania","https://iptv-org.github.io/epg/guides/ro/programetv.ro.epg.xml",true,"ro")),
"pl" to listOf(EpgSource("Polonia","https://iptv-org.github.io/epg/guides/pl/programtv.onet.pl.epg.xml",true,"pl")),
"bg" to listOf(EpgSource("Bulgaria","https://iptv-org.github.io/epg/guides/bg/vivacom.bg.epg.xml",true,"bg")),
"bk" to listOf(EpgSource("Balcani · Serbia","https://iptv-org.github.io/epg/guides/rs/mts.rs.epg.xml",true,"bk"),EpgSource("Balcani · Bosnia","https://iptv-org.github.io/epg/guides/ba/mtel.ba.epg.xml",true,"bk"))
)

@Composable fun TvGuideScreen(onOpenStream:(String,String,String)->Unit){
 val context=LocalContext.current;val prefs=remember{context.getSharedPreferences("tvguide_epg",0)}
 var country by rememberSaveable{mutableStateOf("it")};var dayOffset by rememberSaveable{mutableStateOf(0)}
 var channels by remember{mutableStateOf<List<Channel>>(emptyList())};var message by remember{mutableStateOf("Caricamento guida TV…")}
 var pending by remember{mutableStateOf<Channel?>(null)};var showEpg by rememberSaveable{mutableStateOf(false)}
 var sourceName by rememberSaveable{mutableStateOf("")};var sourceUrl by rememberSaveable{mutableStateOf("")};var revision by remember{mutableStateOf(0)}
 var sources by remember{mutableStateOf(loadSources(prefs.getString("sources","[]").orEmpty()))}
 val day=remember(dayOffset){LocalDate.now(zone).plusDays(dayOffset.toLong())}
 LaunchedEffect(country,day,revision,sources){message="Caricamento canali e programmi…";try{channels=withContext(Dispatchers.IO){mergeEpg(loadChannels(country,day),(defaultEpg[country].orEmpty()+sources.filter{it.enabled&&(it.country.isBlank()||it.country==country)}),day)};message=if(channels.isEmpty())"Nessun canale disponibile" else ""}catch(e:Exception){message="Guida non disponibile: "+(e.localizedMessage?:"errore di rete")}}
 LaunchedEffect(pending){val ch=pending?:return@LaunchedEffect;try{onOpenStream(withContext(Dispatchers.IO){resolveStream(ch.id)},ch.name,ch.id)}catch(e:Exception){message="Impossibile aprire "+ch.name}finally{pending=null}}
 Column(Modifier.fillMaxSize().background(Color(0xFF10131D)).padding(start=36.dp,top=24.dp,end=24.dp)){
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Text("Guida TV · TvVoo",color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Bold);GuideButton("EPG ("+(defaultEpg[country].orEmpty().size+sources.count{it.enabled})+")"){showEpg=!showEpg};GuideButton("Aggiorna EPG"){revision++}}
  if(showEpg){Column(Modifier.fillMaxWidth().background(Color(0xFF1B2030),RoundedCornerShape(8.dp)).padding(10.dp)){Text("Sorgenti XMLTV",color=Color.White,fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextField(sourceName,{sourceName=it},label={Text("Nome")},modifier=Modifier.width(180.dp));TextField(sourceUrl,{sourceUrl=it},label={Text("URL XMLTV")},modifier=Modifier.width(500.dp));GuideButton("Aggiungi"){if(sourceUrl.startsWith("http")){sources=sources+EpgSource(sourceName.ifBlank{"EPG "+(sources.size+1)},sourceUrl);saveSources(prefs,sources);sourceName="";sourceUrl="";revision++}}}
   sources.forEachIndexed{i,s->Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(top=5.dp)){GuideButton((if(s.enabled)"✓ " else "○ ")+s.name,selected=s.enabled){sources=sources.toMutableList().also{it[i]=s.copy(enabled=!s.enabled)};saveSources(prefs,sources);revision++};Text(s.url,color=Color.LightGray,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.width(480.dp).padding(10.dp));GuideButton("Elimina"){sources=sources.toMutableList().also{it.removeAt(i)};saveSources(prefs,sources);revision++}}}
  }}
  Spacer(Modifier.height(10.dp));Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){countries.forEach{(c,n)->GuideButton(n,selected=country==c){country=c}}}
  Spacer(Modifier.height(10.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){GuideButton("← Giorno prima"){dayOffset--};Text(day.format(DateTimeFormatter.ofPattern("EEEE d MMMM",Locale.ITALIAN)),color=Color.White,modifier=Modifier.padding(12.dp),fontSize=18.sp);GuideButton("Giorno dopo →"){dayOffset++};if(dayOffset!=0)GuideButton("Oggi"){dayOffset=0}}
  if(message.isNotEmpty())Text(message,color=Color.White,modifier=Modifier.padding(12.dp))
  LazyColumn(verticalArrangement=Arrangement.spacedBy(5.dp)){items(channels,key={it.id}){ch->Row(Modifier.fillMaxWidth().height(86.dp).background(Color(0xFF1B2030),RoundedCornerShape(8.dp)),horizontalArrangement=Arrangement.spacedBy(8.dp)){ChannelButton(ch,Modifier.width(210.dp).height(82.dp)){if(pending==null)pending=ch};Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){if(ch.programmes.isEmpty())Text("Palinsesto non disponibile",color=Color.LightGray,modifier=Modifier.padding(20.dp))else visibleProgrammes(ch.programmes,day).forEach{p->val now=Instant.now();val live=day==LocalDate.now(zone)&&!p.start.isAfter(now)&&p.end.isAfter(now);GuideButton((if(live)"● IN ONDA  " else "")+tf.format(p.start)+"–"+tf.format(p.end)+"\n"+p.title,Modifier.width(235.dp).height(82.dp),selected=live){if(pending==null)pending=ch}}}}}}
 }
}
@Composable private fun ChannelButton(ch:Channel,modifier:Modifier,onClick:()->Unit){var f by remember{mutableStateOf(false)};Row(modifier.onFocusChanged{f=it.isFocused}.border(if(f)2.dp else 0.dp,Color.White,RoundedCornerShape(7.dp)).background(if(f)Color(0xFF3867AC) else Color(0xFF30394D),RoundedCornerShape(7.dp)).clickable(onClick=onClick).padding(7.dp)){if(ch.logo.isNotBlank())NetworkImage(ch.logo,Modifier.size(64.dp));Text(ch.name,color=Color.White,fontSize=15.sp,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(8.dp))}}
@Composable private fun NetworkImage(url:String,modifier:Modifier){var b by remember(url){mutableStateOf<android.graphics.Bitmap?>(null)};LaunchedEffect(url){b=withContext(Dispatchers.IO){try{URL(url).openStream().use{BitmapFactory.decodeStream(it)}}catch(_:Exception){null}}};b?.let{androidx.compose.foundation.Image(it.asImageBitmap(),null,modifier)}}
@Composable private fun GuideButton(label:String,modifier:Modifier=Modifier,selected:Boolean=false,onClick:()->Unit){var f by remember{mutableStateOf(false)};Text(label,maxLines=2,overflow=TextOverflow.Ellipsis,color=Color.White,fontSize=16.sp,modifier=modifier.onFocusChanged{f=it.isFocused}.border(if(f)2.dp else 0.dp,Color.White,RoundedCornerShape(7.dp)).background(if(selected||f)Color(0xFF3867AC) else Color(0xFF30394D),RoundedCornerShape(7.dp)).clickable(onClick=onClick).padding(horizontal=14.dp,vertical=10.dp))}
private fun loadChannels(country:String,day:LocalDate):List<Channel>{val out=ArrayList<Channel>();for(skip in 0..4900 step 100){val j=fetchJson(ADDON+"/catalog/tv/vavoo_tv_"+country+"/date="+day+"&skip="+skip+".json");val a=j.optJSONArray("metasDetailed")?:j.optJSONArray("metas")?:break;if(a.length()==0)break;for(i in 0 until a.length()){val x=a.optJSONObject(i)?:continue;val id=x.optString("id");if(id.isBlank())continue;val ps=ArrayList<Programme>();val v=x.optJSONArray("videos");if(v!=null)for(k in 0 until v.length()){val q=v.optJSONObject(k)?:continue;try{ps+=Programme(q.optString("title","Programma"),Instant.parse(q.getString("startTime")),Instant.parse(q.getString("endTime")))}catch(_:Exception){}};val logo=listOf("poster","logo","background","thumbnail").map{x.optString(it)}.firstOrNull{it.startsWith("http")}.orEmpty();out+=Channel(id,x.optString("name","Canale"),logo,ps.sortedBy{it.start})};if(a.length()<100)break};return out.distinctBy{it.id}}
private fun mergeEpg(base:List<Channel>,sources:List<EpgSource>,day:LocalDate):List<Channel>{val data=sources.mapNotNull{try{parseXmlTv(it.url,day)}catch(_:Exception){null}};return base.map{ch->if(ch.programmes.isNotEmpty())ch else{val n=norm(ch.name);var hit:List<Programme>?=null;for(d in data){val id=d.names.entries.firstOrNull{norm(it.key)==norm(ch.id)||it.value.any{z->norm(z)==n}}?.key;if(id!=null&&!d.programmes[id].isNullOrEmpty()){hit=d.programmes[id];break}};ch.copy(programmes=hit?:emptyList())}}}
private fun parseXmlTv(address:String,day:LocalDate):EpgData{val c=URL(address).openConnection() as HttpURLConnection;c.connectTimeout=15000;c.readTimeout=30000;try{val p=XmlPullParserFactory.newInstance().newPullParser();p.setInput(c.inputStream,"UTF-8");val names=HashMap<String,List<String>>();val progs=HashMap<String,MutableList<Programme>>();var e=p.eventType;while(e!=XmlPullParser.END_DOCUMENT){if(e==XmlPullParser.START_TAG&&p.name=="channel"){val id=p.getAttributeValue(null,"id").orEmpty();val ns=ArrayList<String>();var z=p.next();while(!(z==XmlPullParser.END_TAG&&p.name=="channel")){if(z==XmlPullParser.START_TAG&&p.name=="display-name")ns+=p.nextText();z=p.next()};names[id]=ns}else if(e==XmlPullParser.START_TAG&&p.name=="programme"){val id=p.getAttributeValue(null,"channel").orEmpty();val st=parseTime(p.getAttributeValue(null,"start"));val en=parseTime(p.getAttributeValue(null,"stop"));var title="Programma";var z=p.next();while(!(z==XmlPullParser.END_TAG&&p.name=="programme")){if(z==XmlPullParser.START_TAG&&p.name=="title")title=p.nextText();z=p.next()};if(st!=null&&en!=null&&st.atZone(zone).toLocalDate()==day)progs.getOrPut(id){ArrayList()}+=Programme(title,st,en)};e=p.next()};return EpgData(names,progs.mapValues{it.value.sortedBy{q->q.start}})}finally{c.disconnect()}}
private fun parseTime(v:String?):Instant?{if(v.isNullOrBlank())return null;return try{val m=Regex("^(\\d{8})(\\d{4})(\\d{2})?\\s*(Z|[+-]\\d{4}|[A-Za-z_]+(?:/[A-Za-z_]+)?)?.*").find(v.trim())?:return null;val raw=m.groupValues[1]+m.groupValues[2]+m.groupValues[3].ifBlank{"00"};val l=LocalDateTime.parse(raw,DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));val tz=m.groupValues.getOrNull(4).orEmpty();when{tz=="Z"->l.toInstant(ZoneOffset.UTC);Regex("[+-]\\d{4}").matches(tz)->l.toInstant(ZoneOffset.of(tz.substring(0,3)+":"+tz.substring(3)));tz.isNotBlank()->l.atZone(ZoneId.of(tz)).toInstant();else->l.atZone(zone).toInstant()}}catch(_:Exception){null}}
private fun norm(s:String):String{var x=Normalizer.normalize(s,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").uppercase(Locale.ROOT);x=x.replace(Regex("\\((BACKUP|HD|FHD|UHD|4K)\\)")," ").replace(Regex("\\b(BACKUP|FHD|UHD|4K|HD)\\b")," ");return x.replace(Regex("[^A-Z0-9]+")," ").trim()}
private fun loadSources(raw:String):List<EpgSource>{return try{val a=JSONArray(raw);(0 until a.length()).map{val o=a.getJSONObject(it);EpgSource(o.optString("name"),o.optString("url"),o.optBoolean("enabled",true))}}catch(_:Exception){emptyList()}}
private fun saveSources(p:android.content.SharedPreferences,s:List<EpgSource>){val a=JSONArray();s.forEach{a.put(JSONObject().put("name",it.name).put("url",it.url).put("enabled",it.enabled))};p.edit().putString("sources",a.toString()).apply()}
private fun resolveStream(id:String):String{val e=URLEncoder.encode(id,"UTF-8").replace("+","%20");val a=fetchJson(ADDON+"/stream/tv/"+e+".json").optJSONArray("streams")?:error("nessuno stream");for(i in 0 until a.length()){val u=a.optJSONObject(i)?.optString("url").orEmpty();if(u.startsWith("http"))return u};error("nessuno stream riproducibile")}
private fun fetchJson(address:String):JSONObject{val c=URL(address).openConnection() as HttpURLConnection;c.connectTimeout=15000;c.readTimeout=25000;try{if(c.responseCode !in 200..299)error("HTTP "+c.responseCode);return JSONObject(c.inputStream.bufferedReader().use{it.readText()})}finally{c.disconnect()}}

private fun visibleProgrammes(programmes:List<Programme>,day:LocalDate):List<Programme>{if(day!=LocalDate.now(zone))return programmes;val now=Instant.now();val current=programmes.indexOfFirst{!it.start.isAfter(now)&&it.end.isAfter(now)};if(current>=0)return programmes.drop(current);val next=programmes.indexOfFirst{it.start.isAfter(now)};return if(next>=0)programmes.drop(next) else programmes.takeLast(1)}
