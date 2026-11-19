package com.alaa.universe

import java.text.Normalizer
import java.util.Locale

/** Playlist-independent, offline name classification. Does not modify provider IDs. */
object CategoryIcons {
    data class Icon(val badge:String,val kind:String)
    private fun normalized(value:String)=Normalizer.normalize(value.lowercase(Locale.ROOT),Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"),"").replace("أ","ا").replace("إ","ا").replace("آ","ا").replace("ى","ي")
        .replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
    private fun has(name:String,aliases:String)=aliases.split(";").any{alias->
        (" "+name+" ").contains(" "+normalized(alias)+" ")
    }
    private val brands=listOf(
        "N" to "netflix;نتفليكس;نتفلكس",
        "Disney+" to "disney;disneyplus;ديزني",
        "prime" to "amazon;prime;امازون;برايم",
        "tv+" to "apple tv;appletv;ابل",
        "HBO" to "hbo;hbo max",
        "MAX" to "max",
        "شاهد" to "shahid;شاهد",
        "OSN" to "osn;او اس ان",
        "beIN" to "bein;be in;بي ان",
        "MBC" to "mbc;ام بي سي",
        "STARZ" to "starz;starzplay;ستارز",
        "P+" to "paramount;باراماونت",
        "Hulu" to "hulu", "D+" to "discovery;discoveryplus",
        "DAZN" to "dazn"
    )
    private val countries=listOf(
        "🇸🇾" to "sy;syr;syria;syrian;syrien;سوريا;سورية;سوري",
        "🇹🇷" to "tr;tur;turkey;turkiye;turkish;türkei;türkisch;تركي;تركية;تركيا",
        "🇩🇪" to "de;deu;ger;germany;german;deutsch;deutschland;المانيا;الماني;ألماني",
        "🇬🇧" to "uk;gb;gbr;united kingdom;britain;british;england;بريطانيا;انجلترا",
        "🇺🇸" to "us;usa;united states;america;american;امريكا;امريكي",
        "🇫🇷" to "fr;fra;france;french;frankreich;فرنسا;فرنسي",
        "🇪🇸" to "es;esp;spain;spanish;spanien;اسبانيا;اسباني",
        "🇮🇹" to "it;ita;italy;italian;italien;ايطاليا;ايطالي",
        "🇳🇱" to "nl;nld;netherlands;dutch;holland;هولندا;هولندي",
        "🇵🇹" to "pt;prt;portugal;portuguese;البرتغال",
        "🇬🇷" to "gr;grc;greece;greek;griechenland;اليونان",
        "🇸🇦" to "sa;ksa;saudi;saudi arabia;السعودية;سعودي",
        "🇪🇬" to "eg;egy;egypt;egyptian;agypten;مصر;مصري",
        "🇱🇧" to "lb;lbn;lebanon;libanon;لبنان;لبناني",
        "🇮🇶" to "iq;irq;iraq;irak;العراق;عراقي",
        "🇯🇴" to "jo;jor;jordan;الاردن;اردني",
        "🇦🇪" to "ae;uae;emirates;الامارات;اماراتي",
        "🇶🇦" to "qa;qat;qatar;قطر", "🇰🇼" to "kw;kwt;kuwait;الكويت",
        "🇧🇭" to "bh;bhr;bahrain;البحرين", "🇴🇲" to "om;omn;oman;عمان",
        "🇵🇸" to "ps;pse;palestine;palastina;فلسطين;فلسطيني",
        "🇲🇦" to "ma;mar;morocco;marokko;المغرب;مغربي",
        "🇩🇿" to "dz;dza;algeria;algerien;الجزائر;جزائري",
        "🇹🇳" to "tn;tun;tunisia;tunesien;تونس;تونسي",
        "🇱🇾" to "ly;lby;libya;libyen;ليبيا", "🇸🇩" to "sd;sdn;sudan;السودان",
        "🇾🇪" to "ye;yem;yemen;اليمن",
        "🇮🇳" to "in;ind;india;indian;hindi;indien;الهند;هندي",
        "🇵🇰" to "pk;pak;pakistan;urdu;باكستان",
        "🇮🇷" to "ir;irn;iran;persian;farsi;ايران;فارسي",
        "🇷🇺" to "ru;rus;russia;russian;russland;روسيا;روسي",
        "🇺🇦" to "ua;ukr;ukraine;اوكرانيا",
        "🇸🇪" to "se;swe;sweden;swedish;schweden;السويد",
        "🇳🇴" to "no;nor;norway;norwegian;norwegen;النرويج",
        "🇩🇰" to "dk;dnk;denmark;danish;danemark;الدنمارك",
        "🇫🇮" to "fi;fin;finland;finnish;فنلندا",
        "🇵🇱" to "pl;pol;poland;polish;polen;بولندا",
        "🇷🇴" to "ro;rou;romania;romanian;rumanien;رومانيا",
        "🇧🇷" to "br;bra;brazil;brasil;البرازيل",
        "🇨🇦" to "ca;can;canada;kanada;كندا",
        "🇦🇺" to "au;aus;australia;australien;استراليا",
        "🇨🇭" to "ch;che;switzerland;schweiz;سويسرا",
        "🇦🇹" to "at;aut;austria;osterreich;النمسا",
        "🇧🇪" to "be;bel;belgium;belgien;بلجيكا",
        "🇦🇱" to "al;alb;albania;albanien;البانيا",
        "🇮🇪" to "ie;irl;ireland;irland;ايرلندا",
        "🇲🇽" to "mx;mex;mexico;mexiko;المكسيك",
        "🇨🇿" to "cz;cze;czech;tschechien;التشيك",
        "🇭🇷" to "hr;hrv;croatia;kroatien;كرواتيا",
        "🇨🇾" to "cy;cyp;cyprus;zypern;قبرص",
        "🇳🇿" to "nz;nzl;new zealand;neuseeland;نيوزيلندا",
        "🇨🇷" to "cr;cri;costa rica;كوستاريكا",
        "🇮🇩" to "id;idn;indonesia;indonesien;اندونيسيا",
        "🇮🇸" to "is;isl;iceland;island;ايسلندا",
        "🇭🇺" to "hu;hun;hungary;ungarn;المجر",
        "🇷🇸" to "rs;srb;serbia;serbien;صربيا",
        "🇲🇰" to "mk;mkd;macedonia;mazedonien;مقدونيا",
        "🇸🇮" to "si;svn;slovenia;slowenien;سلوفينيا",
        "🇧🇬" to "bg;bgr;bulgaria;bulgarien;بلغاريا",
        "🇰🇷" to "kr;kor;korea;كوريا",
        "🇯🇵" to "jp;jpn;japan;japanese;اليابان",
        "🇵🇭" to "ph;phl;philippines;philippinen;الفلبين",
        "🇬🇪" to "ge;geo;georgia;georgien;جورجيا",
        "🇦🇿" to "az;aze;azerbaijan;aserbaidschan;اذربيجان",
        "🇦🇲" to "am;arm;armenia;armenien;ارمينيا",
        "🇻🇪" to "ve;ven;venezuela;فنزويلا",
        "🇭🇰" to "hk;hkg;hong kong;هونغ كونغ",
        "🇹🇭" to "th;tha;thailand;تايلاند",
        "🇰🇿" to "kz;kaz;kazakhstan;kasachstan;كازاخستان",
        "🇱🇹" to "lt;ltu;lithuania;litauen;ليتوانيا",
        "🇺🇿" to "uz;uzb;uzbekistan;اوزبكستان",
        "🇧🇴" to "bo;bol;bolivia;بوليفيا",
        "🇿🇦" to "za;zaf;south africa;جنوب افريقيا",
        "🇨🇳" to "cn;chn;china;chinese;الصين"
    )
    fun classify(raw:String):Icon {
        val n=normalized(raw)
        brands.firstOrNull{has(n,it.second)}?.let{return Icon(it.first,"brand")}
        countries.firstOrNull{has(n,it.second)}?.let{return Icon(it.first,"country")}
        if(has(n,"ar;arab;arabic;arabisch;عربي;عربية;العربية;العرب;الدول العربية;arab countries"))return Icon("🌍","region")
        if(has(n,"asia;asian;اسيا;اسيوي"))return Icon("🌏","region")
        if(has(n,"afr;africa;african;افريقيا;افريقي"))return Icon("🌍","region")
        if(has(n,"la;latino;latin america;امريكا اللاتينية"))return Icon("🌎","region")
        if(has(n,"ku;kurdish;kurdisch;كردي;كردية;کوردی"))return Icon("☀","region")
        if(has(n,"exyu;balkan;balkans;البلقان"))return Icon("🌍","region")
        val genres=listOf(
            "⭐" to "favorites;المفضلة", "🕘" to "recent;recently viewed;المشاهدة الاخيرة",
            "⚽" to "sport;sports;sportkanale;رياضة;رياضي;رياضية",
            "🧸" to "kids;children;cartoon;kinder;اطفال;الاطفال;كرتون",
            "📰" to "news;nachrichten;اخبار;الاخبار;اخبارية",
            "🎥" to "documentary;documentaries;doku;dokumentation;وثائقي;وثائقية",
            "🎵" to "music;musik;موسيقي;موسيقى;اغاني",
            "🕌" to "religious;religion;islam;quran;koran;قران;القران;ديني;دينية;اسلامية",
            "🎬" to "movie;movies;cinema;film;filme;افلام;الافلام",
            "📺" to "series;serien;مسلسلات;المسلسلات"
        )
        genres.firstOrNull{has(n,it.second)}?.let{return Icon(it.first,"genre")}
        return Icon("▦","generic")
    }
    fun decorate(view:android.widget.TextView){
        val original=view.text.toString()
        val icon=classify(original)
        val value=android.text.SpannableString(label(original))
        if(icon.kind=="brand"){
            val color=when(icon.badge){
                "N"->0xffe50914.toInt()
                "prime"->0xff00a8e1.toInt()
                "Disney+"->0xff72d9e8.toInt()
                "شاهد"->0xff00d2aa.toInt()
                else->0xffa8c8ff.toInt()
            }
            value.setSpan(android.text.style.ForegroundColorSpan(color),0,icon.badge.length,0)
            value.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD),0,icon.badge.length,0)
        }
        view.text=value
    }
    fun label(name:String)=classify(name).badge+"  "+name
}
