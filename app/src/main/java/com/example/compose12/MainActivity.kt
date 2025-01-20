package com.example.compose12
/*Aplicación para guardar, eliminar y listar contactos usando Room*/
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.lifecycleScope
import com.example.compose12.ui.theme.Compose12Theme
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


// 1. Modelo de datos Room (Entidad)
@Entity(tableName = "contactos")
data class Contacto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String,
    val mail: String
)

// 2. DAO (Interfaz para interactuar con la base de datos)
@Dao
interface ContactoDao{
    @Query("SELECT * FROM contactos")
    suspend fun getTodosContactos(): List<Contacto>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarContacto(contacto: Contacto)

    @Delete
    suspend fun eliminarContacto(contacto: Contacto)
}

// 3. Base de datos Room
@Database(entities = [Contacto::class], version = 1, exportSchema = false)
abstract class ContactosDatabase : RoomDatabase(){
    abstract fun contactoDao(): ContactoDao

    companion object{
        @Volatile
        private var INSTANCE : ContactosDatabase? = null

        fun getDatabase(context: Context) : ContactosDatabase{
            return INSTANCE ?: synchronized(this){
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ContactosDatabase::class.java,
                    "contactos_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}


class MainActivity : ComponentActivity() {

    private lateinit var database: ContactosDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = ContactosDatabase.getDatabase(this)
        //leerContactos(this)
        setContent {
            Compose12Theme {
                GestionaContactos(database)
            }
        }
    }
}



val colorBoton = Color(0xFF4A37E5)//color para el botón de Agregar


@Composable
fun GestionaContactos(database: ContactosDatabase){

    val context = LocalContext.current
    val contactosList = remember { mutableStateListOf<Contacto>() }

    //Cargar contactos al iniciar:
    LaunchedEffect (Unit){
        val contactos = withContext(Dispatchers.IO){
            database.contactoDao().getTodosContactos()
        }
        contactosList.addAll(contactos)
    }


    Column (modifier = Modifier.fillMaxSize().padding(10.dp)){
        var nombre by rememberSaveable { mutableStateOf("") } //estado del nombre introducido por el usuario
        var email by rememberSaveable { mutableStateOf("") } //estado del email introducido por el usuario

        val nomRegex = Regex("^[a-zA-ZáéíóúüÁÉÍÓÚÜçÇñÑ ]+$")//Expresión regular para validar el nombre
        val emailRegex = Regex("^(?![._-])(?!.*[.]{2})(?!.*[-]{2})(?!.*[_]{2})[a-zA-Z0-9._-]+[a-zA-Z0-9]+@(?![-])(?!.*[-]{2})[a-zA-Z0-9-]+[a-zA-Z0-9]+(\\.[a-zA-Z]{2,4}){1,2}$"//Expresión regular para validar el email
             //"^(?![.])[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
            //"^(?![_.-])[a-zA-Z0-9]+(?:[._-][a-zA-Z0-9]+)*@[a-zA-Z0-9-]+(?:\\\\.[a-zA-Z]{2,})+\$"
        )

        //Textfield para el nombre
        OutlinedTextField(
            value = nombre,
            onValueChange = {// nombre = soloLetras(it) //solo permitimos letras y espacios en el nombre
                if(nomRegex.matches(it))
                    nombre = reemplazarEspaciosDuplicados(it)//sólo se permite el formato de letras y espacios, y se impide más de un espacio entre palabras
            },
            modifier = Modifier.fillMaxWidth().padding(/*top = 5.dp, bottom = 5.dp*/ vertical = 8.dp),
            label = { Text(text = "Nombre de contacto")}
        )
        //Textfield para el email
        OutlinedTextField(
            value = email,
            onValueChange = { //email = reemplazarCaracteresNoPermitidos(it)//no se permite ciertos caracteres en el email
                    email = it.filter { char -> char.isLetterOrDigit() || char in listOf('.', '_','-', '@') }//el email sólo puede contener estos caracteres, se valida posteriormente antes de añadir
            },
            modifier = Modifier.fillMaxWidth().padding(/*top = 5.dp, bottom = 5.dp*/ vertical = 8.dp),
            label = { Text(text = "Correo electrónico")},
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        //Botón Agregar Contacto
        Button(
            onClick = {
                if(nombre.trim().isNotBlank() && email.trim().isNotBlank()) {//si los campos no están vacíos

                    if (emailRegex.matches(email.trim()) /*&& nomRegex.matches(nombre.trim())*/) {//valida el email, si es correcto
                        val nuevoContacto = Contacto(nombre = nombre.trim(), mail = email.trim())

                        //Guardar en la base de datos y actualizar la lista
                        val scope = (context as ComponentActivity).lifecycleScope

                        scope.launch (Dispatchers.IO){
                            try{
                                database.contactoDao().insertarContacto(nuevoContacto)
                                withContext(Dispatchers.Main){
                                    contactosList.add(nuevoContacto) //añade a contactos

                                }
                            }catch (e: Exception){
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context, "Error al guardar el contacto: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }

                        }

                        nombre = ""//se inicializa nuevamente
                        email = ""//se inicializa nuevamente

                    } else {//de lo contrario muestra mensaje
                        Toast.makeText(context, "Email no válido", Toast.LENGTH_SHORT).show()
                    }
                }else {//De lo contrario se muestra un mensaje
                    val mensaje = "El nombre de contacto y/o el mail deben colocarse para agregar el contacto"
                    Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(10),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorBoton
            )
        ) {
            Text(text = "Agregar contacto", modifier = Modifier.fillMaxWidth(), fontSize = 18.sp, textAlign = TextAlign.Start)
        }//fin de Button

        //LazyColumn
        LazyColumn (modifier = Modifier.fillMaxWidth().padding(16.dp)){ //Se muestran todos los contactos o se elimina el contacto seleccionado

            itemsIndexed(contactosList) { indice, contacto ->
                MostrarContacto(indice, contacto, contactosList, database)
            }
        }//fin de LazyColumn
    }//fin de Column
}

//Función que muestra el nombre y el mail de cada Contacto, y lo elimina si clicka el iconButton
@Composable
fun MostrarContacto(indice: Int, contacto: Contacto, contactosList: MutableList<Contacto>, database: ContactosDatabase){
    val context = LocalContext.current
    Row (Modifier.fillMaxWidth()) {
        Column (Modifier.padding(start = 10.dp).weight(5f)) {
            Text(text= "Nombre: ${contacto.nombre}")
            Text(text= "Correo: ${contacto.mail}")
        }//fin de Column
        MySpacer(20)

        IconButton(
            onClick = {
                val scope = (context as ComponentActivity).lifecycleScope
                scope.launch (Dispatchers.IO){
                    try{
                        database.contactoDao().eliminarContacto(contacto)
                        withContext(Dispatchers.Main){
                            contactosList.removeAt(indice) //elimina el contacto según el índice de la lista pasado como parámetro

                        }
                    }catch (e: Exception){
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Error al eliminar el contacto: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                }
            },
            modifier = Modifier.padding(start = 10.dp).weight(1f)
        ) {
            Icon(imageVector = Icons.Default.Delete,
                contentDescription = "Icono del botón eliminar contacto",
                modifier = Modifier.size(24.dp)
            )
        }//fin de IconButton
    }//fin de Row
    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 5.dp),
        thickness = 1.dp, color = Color.Black)
}
//Función para dar espacios entre objetos, tanto vertical como horizontal
@Composable
fun MySpacer(medida: Int){
    Spacer(modifier = Modifier.width(medida.dp).height(medida.dp))
}
//Función para transformar el texto dado con letras o espacios
fun soloLetras(texto: String): String {
    var nuevo=""
    for(i in 0 until texto.length){
        val c:Char = texto[i]
        if(c.isLetter() || c==' ')
            nuevo+=c
        else
            nuevo+=""
    }
    return nuevo
}
//Función para evitar caracteres no permitidos
/*fun reemplazarCaracteresNoPermitidos(texto: String): String {
    val caracteresARemplazar = setOf('"','!', '#', '$', '%', '&', '/',
        '(',')','=','?','¿','¡','|','º','ª','\\',';',':','_','´','+',
        '*','^','`','[',']','{','}','<','>','¬',' ','\t','\'','ñ','Ñ',
        'á','à','é','è', 'í','ó','ú', 'ü','Á','À','È', 'É','Í','Ó', 'Ú',
        'Ü','ç', 'Ç', 'ï', 'Ï')
    return texto.filterNot { it in caracteresARemplazar}
}*/

//Función para evitar colocar más de un espacio
fun reemplazarEspaciosDuplicados(texto: String): String {
    val regex = "\\s+".toRegex() // encuentra uno o más espacios consecutivos
    return regex.replace(texto, " ") // reemplaza todos los espacios consecutivos por un solo espacio
}
//Función para leer los contactos guardados en el fichero contactos.txt
/*fun leerContactos(context: Context){
    val file = File(context.filesDir, fileName)
    if (file.exists()) {//si existe el fichero
        try {
            val lista = file.readLines()//almacena todas las líneas de contactos.txt
            lista.forEach { linea ->
                val partes = linea.split(";")//separa las partes de la línea
                if (partes.size == 2) {
                    val nombre = partes[0].trim()
                    val email = partes[1].trim()
                    contactosList.add(Contacto(nombre = nombre, mail =  email))
                }
            }
        }catch (e: IOException){//captura error si existiera y muestra mensaje en un Toast
            Toast.makeText(context, "Error al leer el fichero. ${e.message}", Toast.LENGTH_LONG).show()
        }
    }*//*else{
        file.createNewFile()//crea nuevo fichero
    }*//*
}*/
//Función para guardar los contactos en el fichero contactos.txt
/*fun guardarContactos(context: Context) {
    try {
        val file = File(context.filesDir, fileName)
        file.delete()//borra el fichero
        for (contacto in contactosList) {
            file.appendText("${contacto.nombre};${contacto.mail}\n")//crea un nuevo fichero con el nombre dado, y con el contenido en el formato dado
        }
    }catch (e: IOException){//captura error si existiera y muestra mensaje en un Toast
        Toast.makeText(context, "Error al guardar los contactos. ${e.message}", Toast.LENGTH_LONG).show()
    }
}*/



