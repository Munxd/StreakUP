package com.example.streakup

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn          // Jetpack Compose arayüz motoru
import androidx.compose.foundation.lazy.items               // Temel yapıtaşları column row image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.* // State yönetimi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.streakup.ui.theme.StreakUpTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser            // Firebase Auth işlemleri
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential        // Google giriş kütüphaneleri
import androidx.credentials.GetCredentialRequest

// Alışkanlık modelimiz
data class Aliskanlik(
    val id: String = "",
    val ad: String = "",                // Değerler val olarak saklanır
    val tamamlandiMi: Boolean = false,  // Checkbox durumu
    val kullaniciId: String = "",
    val zamanDamgasi: Long = 0          // Sıralama için zaman damgası
)

// Uygulama giriş noktası
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StreakUpTheme {
                AnaUygulamaGezinimi()
            }
        }
    }
}

@Composable
fun AnaUygulamaGezinimi() {
    val auth = FirebaseAuth.getInstance()

    // Remember kullanmamaızın sebebi durum korumak. Activity değiştiğinde verilerin kaybolmamasını sağlamak
    var mevcutKullanici by remember {
        mutableStateOf(
            if (auth.currentUser?.isEmailVerified == true)
                auth.currentUser
            else null
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        if (mevcutKullanici != null) {      // kullanıcı var mı yok mu?
                                            // Kullanıcı giriş yapmışsa ana ekrana yönlendir
            AnaEkran(
                kullanici = mevcutKullanici!!,
                cikisYapinca = {
                    auth.signOut()
                    mevcutKullanici = null
                }
            )
        } else {
                                            // kullanıcı giriş yapmamışsa giriş ekranında kalıyor
            KimlikEkrani(girisBasarili = { mevcutKullanici = it })
        }
    }
}

// 2 tane parametre alıyor birincisi mevcutkullanıcı ikincisi cikisYapinca diye bir anonim fonksiyon parametre almıyor -> Unit ile de değer döndürmüyor
// composable bu fonksiyonun ekrana bir şeyler çizeceğini belirtir
@Composable
fun AnaEkran(kullanici: FirebaseUser, cikisYapinca: () -> Unit) { // firebaseden gelen o anki kullanıcının tüm bilgileri
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance() // firebase firestore veritabanına erişim sağlamak veri okumak veri yazmak


    // Mail ile kaydolan birinin ismi yoksa Firebase'de displayName boş gelir. Google ile girenlerde ise genellikle dolu gelir.
    var ekrandakiIsim by remember { mutableStateOf(kullanici.displayName ?: "") }
    var isimBelirlemePenceresi by remember { mutableStateOf(false) }

    // Eğer kullanıcının ismi yoksa zorunlu olarak isim belirleme penceresini açar
    LaunchedEffect(Unit) {
        if (ekrandakiIsim.isEmpty()) {
            isimBelirlemePenceresi = true
        }
    }

    val aliskanliklar = remember { mutableStateListOf<Aliskanlik>() }

    // Progressbar hesabı
    val ilerleme = if (aliskanliklar.isEmpty()) 0f
    else aliskanliklar.count { it.tamamlandiMi }.toFloat() / aliskanliklar.size

    // Firestore'dan alışkanlıkları çekiyoruz
    LaunchedEffect(kullanici.uid) {
        db.collection("aliskanliklarim") // veritabanında alışkanlıklarım klasörüne gideriz
            .whereEqualTo("kullaniciId", kullanici.uid) //kullanızı ID sine göre verileri getirir başkasının verisini getirmez
            .orderBy("zamanDamgasi", Query.Direction.ASCENDING) // sıralarına göre
            .addSnapshotListener { snapshot, hata ->
                if (hata != null) return@addSnapshotListener
                                                                // veritabanını diler veritabanında değişiklik oldugu an tetiklenir listeyi günceller sayfayı yenilemeye gerek yoktur
                if (snapshot != null) {
                    aliskanliklar.clear()
                    for (doc in snapshot.documents) {
                        val aliskanlik = doc.toObject(Aliskanlik::class.java)
                        if (aliskanlik != null) {
                            aliskanliklar.add(aliskanlik.copy(id = doc.id))
                        }
                    }
                }
            }
    }

    var eklemePenceresiGoster by remember { mutableStateOf(false) }
    var yeniAliskanlikAdi by remember { mutableStateOf("") }
    var yeniKullaniciAdi by remember { mutableStateOf("") } // İsim belirleme için değişken

    // column bütün elemanları alt alta dizer
    // row yatay düzendir soldan sağa doğru sıralar
    // spacer boşluk bırakmamıza yarar
    // ıconbutton yazıya tıklanabilir buton
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {

        // Üst kısım (isim + çıkış)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween, // ilk elemanı en sola koy son elemanı sağa koy spacebetween aradaki boşluğu kendisi hesaplıyor
            verticalAlignment = Alignment.CenterVertically // row içindeki bütün bileşenleri dikeyde ortala Column ile IconButton'ın dikey ortaları aynı hizaya gelir.
        ) {
            Column {
                Text("Hoş geldin,", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (ekrandakiIsim.isNotEmpty()) ekrandakiIsim else "...", // ekranın sol üstüne Hoş geldin, kullanıcı adını yazar eğer kullanıcı adı boşsa ... koyar
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = cikisYapinca) {
                Text("Çıkış", color = Color.Red, fontSize = 12.sp)            // ıconbutton olan çıkış butonumuz da sağ üstte kırmızı olarak buluancaktır
            }
        }

        Spacer(Modifier.height(24.dp))

        // İlerleme barı
        Text("İlerleme", color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { ilerleme },
            modifier = Modifier.fillMaxWidth().height(10.dp) // düm genişliği kaplamasını ve 10 dp yüksekliğinde olmasını sağladım
                .clip(RoundedCornerShape(5.dp)), // daha yumşak bir görüntü için köşeler yuvarlanır
            color = Color(0xFF4285F4), // barın dolu olan kısmının rengi
            trackColor = Color(0xFFE0E0E0) //barın dolu olmayan kısmının rengi
        )

        Spacer(Modifier.height(32.dp))

        Text("Günlük Alışkanlıklar", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // Eğer hiç alışkanlık yoksa aşağıdaki kod çalışır
        if (aliskanliklar.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Henüz alışkanlık eklemedin.", color = Color.Gray)
            }
        } else {
            // alışkanlık varsa Alışkanlık listesi
            // LazyColumn burda çok önemli bir görev üstleniyor eğer 50 alışkanlığımız varsa  normal column bunların hepsini çiziyor
            // bu duurm da performansı çok etkiliyor lazycolumn ekrana ne kadar öğe sığıyorsa o kadar çizer sonra kaydırdıkça geri kalanlarını çiziyor
            LazyColumn(modifier = Modifier.weight(1f)) {        // weight(1f): "Kalan tüm boş alanı kapla" demektir.
                items(aliskanliklar, key = { it.id }) { aliskanlik -> // bir döngüdür listedeki her bir eleman için bir kere çalışır
                // key = { it.id } "Her satırın kimliği, o alışkanlığın idsidir. her satırı benzersiz kılar

                    val guncelle = {
                        val index = aliskanliklar.indexOfFirst { it.id == aliskanlik.id }
                        // hızlı çalışsın diye ilk önce ekranda bir güncelleme yapıyoruz chechos işaretlensin diye
                        if (index != -1) {
                            aliskanliklar[index] =
                                aliskanlik.copy(tamamlandiMi = !aliskanlik.tamamlandiMi)
                        }
                        // sonra da database de tamamlandı mı kısmını güncelliyoruz
                        // db.collection("aliskanliklarim") → Firestore’daki aliskanliklarim koleksiyonuna erişiyorsun.
                        // .document(aliskanlik.id) → Güncellemek istediğin belirli belgenin ID’sini seçiyorsun.
                        // .update("tamamlandiMi", !aliskanlik.tamamlandiMi) → Belgedeki tamamlandiMi alanını !aliskanlik.tamamlandiMi ile değiştiriyorsun.
                        db.collection("aliskanliklarim").document(aliskanlik.id)
                            .update("tamamlandiMi", !aliskanlik.tamamlandiMi)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth() // tüm satırı kaplamasını sağlarız
                            .padding(vertical = 8.dp)
                            .clickable { guncelle() }, // bu özellik ile kutucuk harici yazıya tıklasak bile  güncelle fonksiyonunun çalışmasını sağlarız
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = aliskanlik.tamamlandiMi, // kutu boş mu dolu mu
                            onCheckedChange = { guncelle() }, // kutuya doğrudan basılınca da güncelle fonksiyonu çalışyıor
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF4285F4)
                            )
                        )
                        Text(
                            aliskanlik.ad,
                            fontSize = 18.sp,
                            color = if (aliskanlik.tamamlandiMi) Color.Gray else Color.Black, // dinamik renklendirma tamamlanmışsa gri olacak tamamlanmamışsa siyah
                            modifier = Modifier.padding(start = 12.dp) // yazı ile checkbox arasına boşluk koyar
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Alışkanlık Ekle butonu eğer bu butona tıklarsak aşağıdaki if true olur ve ekrana pencereyi çizer
        Button(
            onClick = { eklemePenceresiGoster = true },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(Color(0xFFF5F5F5)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("+ Alışkanlık Ekle", color = Color(0xFF4285F4), fontSize = 16.sp)
        }
    }

    // Alışkanlık ekleme popup'ı
    if (eklemePenceresiGoster) {
        AlertDialog(
            onDismissRequest = { eklemePenceresiGoster = false }, // kullanıcı ekranda boş bir yere veya iptal tuşuna basınca  pencerenin kapanmasını sağlar
            title = { Text("Yeni Alışkanlık") },
            text = {
                TextField(  // pencerenin içeriği bu kutuda oluşuyor
                    value = yeniAliskanlikAdi, // ne göstereyim?
                    onValueChange = { yeniAliskanlikAdi = it }, // bir şey yazılırsa ne yapayım    // Kullanıcı klavyeye bastığında ekranda harflerin nasıl göründüğünü yöneten kısımdır.
                    placeholder = { Text("Örn: Yatağını Düzenle") } // boşken göstereceği değer
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (yeniAliskanlikAdi.isNotEmpty()) { // kullanıcı boş bir kutuya ekle derse hiç bir şey yapmaz, bir güvenlik önlemi
                            val harita = hashMapOf( //Firestore, verileri JSON benzeri "Anahtar-Değer" (Key-Value) ikilileri olarak saklar. Kotlin'de bunun karşılığı HashMaptir.
                                "ad" to yeniAliskanlikAdi,
                                "tamamlandiMi" to false, // "tamamlandiMi": Varsayılan olarak false. Çünkü yeni eklenen bir alışkanlık henüz yapılmamıştır.
                                "kullaniciId" to kullanici.uid, // "kullaniciId": Çok Kritik. Bu verinin kime ait olduğunu buraya damgalıyoruz.
                                "zamanDamgasi" to System.currentTimeMillis() // eklenen sıraya göre alışkanlıkları sıralama
                            )
                            db.collection("aliskanliklarim").add(harita) // veritabanında aliskanlıklarim klasörüne git add ile haritayı ekle add kullandığımız için firestore otomatik bir ID atıyor
                            yeniAliskanlikAdi = ""      // kullanıcı bir alışkanlık ekledikten sonra Kutunun içini temizle ki kullanıcı bir sonraki sefer açtığında eski yazdığı şey orada kalmasın.
                            eklemePenceresiGoster = false // pencereyi kapat
                        }
                    }
                ) { Text("Ekle") }
            },
            dismissButton = {
                TextButton(onClick = { eklemePenceresiGoster = false }) { // iptal butonu
                    Text("İptal")
                }
            }
        )
    }


    // Eğer kullanıcının ismi yoksa bu pencere açılır ve kapanmaz (onDismissRequest boş)
    // isim belirlemepenceresi true ise çalışır
    if (isimBelirlemePenceresi) {
        AlertDialog(
            onDismissRequest = { /* Zorunlu olduğu için boş bıraktık, dışarı basınca kapanmasın */ },
            title = { Text("Adınızı Belirleyin") },
            text = {
                Column {
                    Text("Devam etmek için lütfen kendinize bir kullanıcı adı belirleyin.") // bilgilendirme metni
                    Spacer(modifier = Modifier.height(16.dp))
                    TextField(
                        value = yeniKullaniciAdi,
                        onValueChange = { yeniKullaniciAdi = it },
                        placeholder = { Text("Adınız Soyadınız") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (yeniKullaniciAdi.isNotEmpty()) { // boş isimle kayıt olmayı engeller
                            // Firebase profilini güncelle
                            val profilGuncelleme = UserProfileChangeRequest.Builder()
                                .setDisplayName(yeniKullaniciAdi)       // kullanıcıdan ismini alır firebase e yollar onay gelince ekranda isim değişir pencere kapanır kullanıcı alışkanlığını girer
                                .build()

                            kullanici.updateProfile(profilGuncelleme)
                                .addOnCompleteListener { gorev ->
                                    if (gorev.isSuccessful) {
                                        ekrandakiIsim = yeniKullaniciAdi // Ekrana yansıt
                                        isimBelirlemePenceresi = false   // Pencereyi kapat
                                    } else {
                                        Toast.makeText(context, "Hata oluştu", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        } else {
                            Toast.makeText(context, "Lütfen bir isim girin.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Kaydet ve Devam Et")
                }
            }
        )
    }
}

@Composable
fun KimlikEkrani(girisBasarili: (FirebaseUser) -> Unit) {   // kullanıcı giriş yaptı kullanıcıların bilgilerini verir ana ekranın açılmasını ister
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // google ile giriş de kullanılıyor
    val auth = FirebaseAuth.getInstance()

    // true = giriş modu, false = kayıt modu
    var girisModu by remember { mutableStateOf(true) }

    var adSoyad by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var sifre by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            if (girisModu) "StreakUp" else "Hesap Oluştur", // başlık dinamiktir kullanıcı kayıt olmaya çalışırken değişir
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(48.dp))

        // Kayıt modunda isim soruluyor
        if (!girisModu) {
            TextField(              // eğer giriş modunda değilsek kayıt modundayızdır ad soyad sadece kayıtta gözükür
                value = adSoyad,
                onValueChange = { adSoyad = it },
                placeholder = { Text("Adın Soyadın") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF5F5F5),
                    unfocusedContainerColor = Color(0xFFF5F5F5)
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
        }

        // Email
        TextField(
            value = email,
            onValueChange = { email = it },
            placeholder = { Text("Email adresi") },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFF5F5F5),
                unfocusedContainerColor = Color(0xFFF5F5F5)
            ),
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        // Şifre
        TextField(
            value = sifre,
            onValueChange = { sifre = it },
            placeholder = { Text("Şifre") },
            visualTransformation = PasswordVisualTransformation(), // Şifreyi **** yapar ve gizler
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), // otomayik düzenlemeyi kapatırız // şifreye uygun karakter klavyesini açar
            modifier = Modifier.fillMaxWidth(), // Textfieldı yatayda tamamen genişletir
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFF5F5F5),
                unfocusedContainerColor = Color(0xFFF5F5F5)
            ),
            shape = RoundedCornerShape(8.dp), // köşeleri yuvarlar
            singleLine = true // tek bir satır olarak ayaralr kullanıcı entera bastığında aşağıya kaymaz satır
        )   // tek satır olur

        Spacer(Modifier.height(24.dp))

        // Giriş / Kayıt butonu
        Button(
            onClick = {
                if (email.isNotEmpty() && sifre.isNotEmpty()) { // ilk önce kutular dolu mu boş mu onu kontrol eder dolu değilse uyarır else e girer ve toast mesajı gösterir

                    if (girisModu) {
                        // Giriş işlemi
                        auth.signInWithEmailAndPassword(email, sifre) // firebbase e bu email ve şifre ile beni içeri al diyoruz
                            .addOnCompleteListener { gorev ->
                                if (gorev.isSuccessful) { // evet bilgiler doğru görev başarılı
                                    val kullanici = auth.currentUser
                                    if (kullanici != null && kullanici.isEmailVerified) { // kullanıcının girdiği mail gerçek mi diye firebase bir doğrulama maili yollar
                                        girisBasarili(kullanici)                          // eğer kullanıcı boş değilse ve mail onaylanmışsa içeri al
                                    } else {
                                        auth.signOut()
                                        Toast.makeText(
                                            context,
                                            "Mail adresinizi doğrulamanız gerekiyor.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Hata: ${gorev.exception?.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                    } else {
                        // Kayıt işlemi
                        if (adSoyad.isNotEmpty()) { // ad ve soyad bölümü dolu mu diye bakıyoruz doluysa devam edeceğiz zorunlu çünkü
                            auth.createUserWithEmailAndPassword(email, sifre) // yeni bir kullanıcı oluşturuyoruz
                                .addOnCompleteListener { gorev ->
                                    if (gorev.isSuccessful) {
                                        val kullanici = auth.currentUser

                                        // Ad soyad ekleme
                                        val profil = UserProfileChangeRequest.Builder()
                                            .setDisplayName(adSoyad).build()
                                        kullanici?.updateProfile(profil)

                                        // Mail doğrulama
                                        kullanici?.sendEmailVerification() // kullanıcıya doğrulama maili yolluyoruz

                                        Toast.makeText(
                                            context,
                                            "Kayıt başarılı! Mailinizi doğrulayın.",
                                            Toast.LENGTH_LONG
                                        ).show()

                                        girisModu = true
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Hata: ${gorev.exception?.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                        } else {
                            Toast.makeText(context, "Adınızı girin.", Toast.LENGTH_SHORT).show() // ad soyad boş bırakılırsa uyarılır
                        }
                    }

                } else {
                    Toast.makeText(context, "Tüm alanları doldurun.", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(Color.Black), // Bu buton, kullanıcının niyetine göre (Giriş/Kayıt) Firebase ile konuşur gelen cevaba göre işlem yapar
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                if (girisModu) "Giriş Yap" else "Kayıt Ol",
                color = Color.White,
                fontSize = 16.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        // Giriş <-> Kayıt modunu değiştir
        Row {
            Text(
                if (girisModu) "Hesabın yok mu? " else "Zaten hesabın var mı? ",
                color = Color.Gray
            )
            Text(
                if (girisModu) "Kayıt Ol" else "Giriş Yap",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    girisModu = !girisModu
                }
            )
        }

        // Şifre sıfırlama
        if (girisModu) {
            TextButton(
                onClick = {
                    if (email.isNotEmpty()) { // kullanıcı e mail kısmını doldurmuşsa çalışır ve ilgili maile bir sıfırlama linki yollar
                        auth.sendPasswordResetEmail(email)
                        Toast.makeText(context, "Sıfırlama maili gönderildi.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Email girin.", Toast.LENGTH_SHORT).show() // e posta kutusu boşsa uyarı fırlatır
                    }
                }
            ) {
                Text("Şifremi unuttum", color = Color.Gray, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()                     // çizgi
        Spacer(Modifier.height(24.dp))

        // Google ile giriş
        OutlinedButton(
            onClick = {
                scope.launch {
                    try {
                        val manager = CredentialManager.create(context) // Google hesabı seçim ekranını, token almayı ve kimlik doğrulama sürecini yöneten merkez bileşen.

                        val googleOption = GetGoogleIdOption.Builder() // Bu yapı, Google ekrana hangi hesapların gösterileceğini belirliyor
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId("799873898750-3pgv1iono7a0b17slpeda8jekhak2iup.apps.googleusercontent.com")
                            .setAutoSelectEnabled(true)
                            .build()

                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(googleOption) // Burada Google’a “bir kullanıcı girişi istiyorum” diyorsun
                            .build()

                        val result = manager.getCredential(context, request)
                        val credential = result.credential

                        if (credential is CustomCredential &&
                            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                        ) {
                            val googleToken = GoogleIdTokenCredential.createFrom(credential.data) // Google sadece ID Token verir.

                            val firebaseKimlik =
                                GoogleAuthProvider.getCredential(googleToken.idToken, null) // Firebase bu token’ı direk kabul etmez → önce Firebase Credential’a dönüştürmen gerekir.

                            auth.signInWithCredential(firebaseKimlik)
                                .addOnCompleteListener { gorev ->
                                    if (gorev.isSuccessful) {
                                        girisBasarili(auth.currentUser!!)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Hata: ${gorev.exception?.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                        }

                    } catch (e: Exception) {
                        if (!e.message.toString().contains("canceled"))
                            Toast.makeText(context, "Hata: ${e.localizedMessage}", Toast.LENGTH_SHORT)
                                .show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            border = ButtonDefaults.outlinedButtonBorder,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black)
        ) {
            Text("Google ile Devam Et", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}