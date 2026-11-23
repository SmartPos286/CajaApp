package com.example.data.repository

import com.example.BuildConfig
import com.example.data.local.PreferencesManager
import com.example.data.remote.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GeminiRepository(private val prefs: PreferencesManager? = null) {
    private val service = RetrofitClient.geminiService

    private val quoteItemSchema = PropertySchema(
        type = "OBJECT",
        description = "Producto y cantidad a incluir en el presupuesto",
        properties = mapOf(
            "productName" to PropertySchema(type = "STRING", description = "Nombre o código del producto del inventario"),
            "quantity" to PropertySchema(type = "NUMBER", description = "Cantidad solicitada (por defecto 1)"),
            "customPriceUsd" to PropertySchema(type = "NUMBER", description = "Precio unitario personalizado en USD (opcional)"),
            "useWholesale" to PropertySchema(type = "BOOLEAN", description = "True si el cliente solicita precio al mayor")
        ),
        required = listOf("productName", "quantity")
    )

    private val functionDeclarations: List<FunctionDeclaration> = listOf(
        FunctionDeclaration(
            name = "queryInventory",
            description = "Consulta la información de stock, precios en USD/Bs, precio al mayor y detalles de productos en el inventario.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "productName" to PropertySchema(type = "STRING", description = "Nombre o palabra clave del producto a consultar")
                ),
                required = listOf("productName")
            )
        ),
        FunctionDeclaration(
            name = "queryLowStock",
            description = "Lista los productos que tienen existencias bajas o agotadas (stock <= 5).",
            parameters = FunctionParameters(type = "OBJECT", properties = emptyMap())
        ),
        FunctionDeclaration(
            name = "registerNewProduct",
            description = "Prepara el registro de un nuevo producto en el inventario.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "name" to PropertySchema(type = "STRING", description = "Nombre descriptivo del producto"),
                    "code" to PropertySchema(type = "STRING", description = "Código o SKU del producto (opcional)"),
                    "category" to PropertySchema(type = "STRING", description = "Categoría del producto"),
                    "stock" to PropertySchema(type = "NUMBER", description = "Cantidad de existencias"),
                    "costPriceUsd" to PropertySchema(type = "NUMBER", description = "Precio de costo en dólares"),
                    "profitPercentage" to PropertySchema(type = "NUMBER", description = "Porcentaje de ganancia sobre el costo (opcional, ej. 30)"),
                    "priceUsd" to PropertySchema(type = "NUMBER", description = "Precio de venta al detal en dólares"),
                    "wholesalePriceUsd" to PropertySchema(type = "NUMBER", description = "Precio de venta al mayor en dólares (opcional)"),
                    "isRecipe" to PropertySchema(type = "BOOLEAN", description = "True si es receta o servicio"),
                    "isTaxExempt" to PropertySchema(type = "BOOLEAN", description = "True si el producto está exento de IVA")
                ),
                required = listOf("name")
            )
        ),
        FunctionDeclaration(
            name = "updatePendingProduct",
            description = "Modifica o corrige en tiempo real cualquier campo del producto pendiente que se está preparando actualmente.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "name" to PropertySchema(type = "STRING", description = "Nuevo nombre del producto si cambia"),
                    "code" to PropertySchema(type = "STRING", description = "Nuevo código/SKU si cambia"),
                    "category" to PropertySchema(type = "STRING", description = "Nueva categoría si cambia"),
                    "stock" to PropertySchema(type = "NUMBER", description = "Nueva cantidad de existencias"),
                    "costPriceUsd" to PropertySchema(type = "NUMBER", description = "Nuevo precio de costo en USD"),
                    "profitPercentage" to PropertySchema(type = "NUMBER", description = "Nuevo porcentaje de ganancia"),
                    "priceUsd" to PropertySchema(type = "NUMBER", description = "Nuevo precio de venta al detal en USD"),
                    "wholesalePriceUsd" to PropertySchema(type = "NUMBER", description = "Nuevo precio al mayor en USD"),
                    "isRecipe" to PropertySchema(type = "BOOLEAN", description = "Si es receta o servicio"),
                    "isTaxExempt" to PropertySchema(type = "BOOLEAN", description = "Si es exento de IVA")
                )
            )
        ),
        FunctionDeclaration(
            name = "addStock",
            description = "Prepara la adición de existencias a un producto existente en el inventario.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "productName" to PropertySchema(type = "STRING", description = "Nombre del producto"),
                    "quantity" to PropertySchema(type = "NUMBER", description = "Cantidad a sumar"),
                    "costPriceUsd" to PropertySchema(type = "NUMBER", description = "Nuevo precio de costo opcional")
                ),
                required = listOf("productName", "quantity")
            )
        ),
        FunctionDeclaration(
            name = "deleteProduct",
            description = "Prepara la eliminación de un producto del inventario.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "productName" to PropertySchema(type = "STRING", description = "Nombre del producto a eliminar")
                ),
                required = listOf("productName")
            )
        ),
        FunctionDeclaration(
            name = "createQuote",
            description = "Prepara y calcula un presupuesto (cotización) para un cliente con uno o más productos del inventario, sin descontar stock. Permite indicar validez, descuento y teléfono WhatsApp.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "customerName" to PropertySchema(type = "STRING", description = "Nombre del cliente (o 'Consumidor Final')"),
                    "customerDocument" to PropertySchema(type = "STRING", description = "Cédula o RIF del cliente (opcional)"),
                    "customerPhone" to PropertySchema(type = "STRING", description = "Teléfono o WhatsApp del cliente (opcional)"),
                    "items" to PropertySchema(
                        type = "ARRAY",
                        description = "Lista de productos y cantidades para el presupuesto",
                        items = quoteItemSchema
                    ),
                    "validityDays" to PropertySchema(type = "INTEGER", description = "Días de validez del presupuesto (por defecto 7)"),
                    "discountUsd" to PropertySchema(type = "NUMBER", description = "Descuento total en dólares (opcional)"),
                    "notesOrConditions" to PropertySchema(type = "STRING", description = "Observaciones o condiciones del presupuesto")
                ),
                required = listOf("items")
            )
        ),
        FunctionDeclaration(
            name = "updatePendingQuote",
            description = "Modifica o corrige en tiempo real el presupuesto pendiente actual: cambia cliente, teléfono, días de validez, descuento, notas, o agrega/reemplaza/quita productos.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "customerName" to PropertySchema(type = "STRING", description = "Nuevo nombre de cliente"),
                    "customerDocument" to PropertySchema(type = "STRING", description = "Nueva cédula o RIF"),
                    "customerPhone" to PropertySchema(type = "STRING", description = "Nuevo teléfono WhatsApp"),
                    "validityDays" to PropertySchema(type = "INTEGER", description = "Nuevos días de validez"),
                    "discountUsd" to PropertySchema(type = "NUMBER", description = "Nuevo descuento en USD"),
                    "notesOrConditions" to PropertySchema(type = "STRING", description = "Nuevas observaciones o condiciones"),
                    "addItems" to PropertySchema(type = "ARRAY", description = "Productos a agregar o sumar al presupuesto pendiente", items = quoteItemSchema),
                    "replaceItems" to PropertySchema(type = "ARRAY", description = "Lista completa que reemplaza los productos del presupuesto pendiente", items = quoteItemSchema),
                    "removeProductName" to PropertySchema(type = "STRING", description = "Nombre de un producto a quitar del presupuesto")
                )
            )
        ),
        FunctionDeclaration(
            name = "queryQuotes",
            description = "Consulta los presupuestos guardados (por número PRE-XXXXX, cliente o estado) para informar al usuario o abrir su ticket.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "query" to PropertySchema(type = "STRING", description = "Número de presupuesto, nombre del cliente o vacío para listar los últimos")
                )
            )
        ),
        FunctionDeclaration(
            name = "registerCustomer",
            description = "Prepara el registro de un nuevo cliente en el sistema.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "name" to PropertySchema(type = "STRING", description = "Nombre completo del cliente"),
                    "documentId" to PropertySchema(type = "STRING", description = "Cédula o RIF del cliente"),
                    "phone" to PropertySchema(type = "STRING", description = "Número de teléfono / WhatsApp"),
                    "address" to PropertySchema(type = "STRING", description = "Dirección")
                ),
                required = listOf("name")
            )
        ),
        FunctionDeclaration(
            name = "queryCustomers",
            description = "Busca clientes registrados por nombre o cédula/RIF para ver su teléfono y datos.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "query" to PropertySchema(type = "STRING", description = "Nombre o cédula/RIF a buscar")
                ),
                required = listOf("query")
            )
        ),
        FunctionDeclaration(
            name = "queryInvoice",
            description = "Consulta el detalle completo de una factura de venta por su número, incluyendo todos los productos vendidos, cliente y métodos de pago.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "invoiceNumber" to PropertySchema(type = "STRING", description = "Número de la factura a consultar")
                ),
                required = listOf("invoiceNumber")
            )
        ),
        FunctionDeclaration(
            name = "getLastInvoiceDetail",
            description = "Obtiene el detalle completo de la última factura de venta registrada (productos vendidos, cantidades, montos, cliente y método de pago).",
            parameters = FunctionParameters(type = "OBJECT", properties = emptyMap())
        ),
        FunctionDeclaration(
            name = "queryInvoicesByDate",
            description = "Consulta y resume las facturas emitidas en una fecha o rango de fechas (ej: 'hoy', 'ayer', 'semana', 'mes', '15/11/2026', o startDate + endDate). Numerará las facturas para poder consultar alguna luego.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "period" to PropertySchema(type = "STRING", description = "Período o fecha: 'hoy', 'ayer', 'semana', 'mes' o fecha dd/MM/yyyy"),
                    "startDate" to PropertySchema(type = "STRING", description = "Fecha inicial dd/MM/yyyy (opcional)"),
                    "endDate" to PropertySchema(type = "STRING", description = "Fecha final dd/MM/yyyy (opcional)")
                )
            )
        ),
        FunctionDeclaration(
            name = "inspectListedInvoice",
            description = "Muestra el detalle completo de productos de una factura específica de la lista devuelta anteriormente (por su posición 1, 2, 3..., número de factura o nombre del cliente).",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "reference" to PropertySchema(type = "STRING", description = "Posición en la lista (ej. '1', '2', 'primera'), número #id o nombre del cliente")
                ),
                required = listOf("reference")
            )
        ),
        FunctionDeclaration(
            name = "queryCasheaStatus",
            description = "Consulta el estado de Cashea: total vendido por Cashea, abonos liquidados, deuda pendiente de Cashea y comisiones descontadas (ingresos no recibidos).",
            parameters = FunctionParameters(type = "OBJECT", properties = emptyMap())
        ),
        FunctionDeclaration(
            name = "queryPendingCredits",
            description = "Consulta las cuentas por cobrar / créditos pendientes de clientes con sus saldos adeudados.",
            parameters = FunctionParameters(type = "OBJECT", properties = emptyMap())
        ),
        FunctionDeclaration(
            name = "queryExpensesSummary",
            description = "Consulta el resumen de gastos operativos registrados ('hoy', 'mes' o 'todos') agrupados por categoría.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "period" to PropertySchema(type = "STRING", description = "Período: 'hoy', 'mes' o 'todos'")
                )
            )
        ),
        FunctionDeclaration(
            name = "getInventoryValuation",
            description = "Calcula el capital invertido al costo, el valor total de venta del inventario actual, ganancia proyectada y conteo de productos agotados o con bajo stock.",
            parameters = FunctionParameters(type = "OBJECT", properties = emptyMap())
        ),
        FunctionDeclaration(
            name = "updateProductPrice",
            description = "Prepara la actualización rápida del precio de venta (y opcionalmente precio al mayor) de un producto existente en el inventario.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "productName" to PropertySchema(type = "STRING", description = "Nombre o código del producto"),
                    "newPriceUsd" to PropertySchema(type = "NUMBER", description = "Nuevo precio de venta al detal en USD"),
                    "newWholesaleUsd" to PropertySchema(type = "NUMBER", description = "Nuevo precio al mayor en USD (opcional)")
                ),
                required = listOf("productName", "newPriceUsd")
            )
        ),
        FunctionDeclaration(
            name = "registerPurchaseInvoice",
            description = "Prepara el registro de una factura de compra a un proveedor.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "supplierName" to PropertySchema(type = "STRING", description = "Nombre del proveedor"),
                    "invoiceNumber" to PropertySchema(type = "STRING", description = "Número de factura del proveedor"),
                    "totalAmount" to PropertySchema(type = "NUMBER", description = "Monto total de la factura en USD"),
                    "description" to PropertySchema(type = "STRING", description = "Detalle breve de la compra")
                ),
                required = listOf("supplierName", "invoiceNumber", "totalAmount")
            )
        ),
        FunctionDeclaration(
            name = "registerExpense",
            description = "Prepara el registro de un gasto operativo del negocio en USD.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "description" to PropertySchema(type = "STRING", description = "Concepto o descripción del gasto"),
                    "amountUsd" to PropertySchema(type = "NUMBER", description = "Monto del gasto en USD"),
                    "category" to PropertySchema(type = "STRING", description = "Categoría del gasto (ej. Servicios, Nómina, Alquiler, Operativo)")
                ),
                required = listOf("description", "amountUsd")
            )
        ),
        FunctionDeclaration(
            name = "updateBcvRate",
            description = "Prepara la actualización manual de la tasa oficial del BCV en Bolívares por Dólar.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "rateBs" to PropertySchema(type = "NUMBER", description = "Nueva tasa BCV en Bolívares por 1 USD")
                ),
                required = listOf("rateBs")
            )
        ),
        FunctionDeclaration(
            name = "checkBcvRate",
            description = "Consulta la tasa de cambio oficial actual del BCV (Bolívares por Dólar).",
            parameters = FunctionParameters(type = "OBJECT", properties = emptyMap())
        ),
        FunctionDeclaration(
            name = "getDailySummary",
            description = "Consulta el resumen financiero de ventas, gastos y balance del día actual.",
            parameters = FunctionParameters(type = "OBJECT", properties = emptyMap())
        ),
        FunctionDeclaration(
            name = "confirmCurrentAction",
            description = "Confirma y guarda definitivamente en la base de datos la acción pendiente actual (producto, presupuesto, stock, cliente, gasto, factura o tasa) cuando el usuario dice 'sí', 'confirma', 'guárdalo' o 'listo'.",
            parameters = FunctionParameters(type = "OBJECT", properties = emptyMap())
        ),
        FunctionDeclaration(
            name = "cancelCurrentAction",
            description = "Cancela y descarta la acción pendiente actual cuando el usuario dice 'no', 'cancela' o 'descarta'.",
            parameters = FunctionParameters(type = "OBJECT", properties = emptyMap())
        ),
        FunctionDeclaration(
            name = "navigateToModule",
            description = "Navega a una pantalla o módulo específico de la aplicación SmartPOS286.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "module" to PropertySchema(
                        type = "STRING",
                        description = "Nombre del módulo: 'pos' (facturación), 'quotes' (presupuestos), 'inventory' (inventario), 'invoices' (facturas), 'cashea' (cashea), 'expenses' (gastos), 'summary' (resumen), 'customers' (clientes), 'suppliers' (proveedores), 'settings' (ajustes)"
                    )
                ),
                required = listOf("module")
            )
        )
    )

    private val tools = listOf(Tool(functionDeclarations = functionDeclarations))

    fun getEffectiveApiKey(): String {
        val customKey = prefs?.geminiCustomApiKey?.trim().orEmpty()
        if (customKey.isNotBlank()) return customKey
        return BuildConfig.GEMINI_API_KEY.trim()
    }

    fun getEffectiveApiKeyOrNull(): String? {
        val key = getEffectiveApiKey()
        if (key.isBlank() || key.equals("demo", ignoreCase = true) || key.equals("YOUR_API_KEY", ignoreCase = true)) {
            return null
        }
        return key
    }

    fun hasValidApiKey(): Boolean = getEffectiveApiKeyOrNull() != null

    fun getEffectiveModel(): String {
        return "gemini-2.5-flash"
    }

    fun getLiveModel(): String = "models/gemini-2.5-flash-native-audio-preview-12-2025"

    fun buildSystemInstruction(
        catalogSummary: String,
        suppliersSummary: String,
        customersSummary: String,
        bcvRate: Double,
        todaySalesSummary: String,
        minProfitMarginPercent: Double? = null,
        pendingActionSummary: String? = null
    ): String {
        val marginRule = if (minProfitMarginPercent != null && minProfitMarginPercent > 0.0) {
            "- Hay una ganancia porcentual mínima configurada de $minProfitMarginPercent%. Si se da costo sin precio de venta en 'registerNewProduct' o 'updatePendingProduct', el sistema calculará el precio de venta automáticamente."
        } else {
            "- Si el usuario da costo sin precio de venta al crear un producto, igualmente puedes preparar 'registerNewProduct' con costPriceUsd para que lo complete."
        }

        val pendingBlock = if (!pendingActionSummary.isNullOrBlank()) {
            """
            ACCIÓN PENDIENTE ACTUAL EN PANTALLA (el usuario puede corregirla o confirmarla en tiempo real):
            $pendingActionSummary
            """.trimIndent()
        } else {
            "ACCIÓN PENDIENTE ACTUAL EN PANTALLA: Ninguna."
        }

        return """
            Eres Oli, el Asistente Inteligente, Consultor Comercial y Experto en Tecnología y Negocios de SmartPOS286.
            Razona paso a paso antes de responder o llamar herramientas para elegir la acción exacta.
            Tasa oficial BCV vigente: $bcvRate Bs/USD.

            $pendingBlock

            RESUMEN DEL DÍA:
            $todaySalesSummary

            INVENTARIO ACTUAL (muestra):
            $catalogSummary

            CLIENTES REGISTRADOS (muestra):
            $customersSummary

            PROVEEDORES REGISTRADOS:
            $suppliersSummary

            CAPACIDADES Y REGLAS ESTRICTAS:
            1. PRESUPUESTOS: Si el usuario pide crear, armar o cotizar un presupuesto, usa 'createQuote' buscando los productos reales del inventario. Si ya hay un presupuesto pendiente y el usuario corrige cantidades, agrega/quita productos, cambia cliente, WhatsApp, validez o descuento, usa 'updatePendingQuote'.
            2. PRODUCTOS Y CORRECCIONES EN VIVO: Usa 'registerNewProduct' para productos nuevos o 'updateProductPrice' para cambiar precios de productos existentes. Si ya hay un producto pendiente y el usuario hace correcciones en vivo (cambia precio, costo, código, porcentaje de ganancia, precio al mayor, stock o exento), usa 'updatePendingProduct'.
            $marginRule
            3. CONFIRMACIÓN POR VOZ/CHAT: Si hay una acción pendiente y el usuario dice "sí", "confirma", "guárdalo" o "listo", llama a 'confirmCurrentAction'. Si dice "no" o "cancela", llama a 'cancelCurrentAction'.
            4. FACTURAS Y CONSULTAS POR FECHA: Usa 'getLastInvoiceDetail' para el detalle de la última factura, 'queryInvoicesByDate' para resúmenes por fecha o rango (numerando resultados), 'inspectListedInvoice' si el usuario pide ver una factura de esa lista anterior (ej. "la 2", "la de María"), y 'queryInvoice' para una factura por número.
            5. FINANZAS E INVENTARIO: Usa 'queryCasheaStatus' (deuda/comisiones de Cashea), 'queryPendingCredits' (cuentas por cobrar), 'queryExpensesSummary' (gastos), 'getInventoryValuation' (valor total de inventario), 'queryLowStock' (bajo stock), 'queryQuotes', 'queryCustomers', 'addStock', 'registerCustomer', 'registerExpense', 'registerPurchaseInvoice', 'updateBcvRate' o 'navigateToModule'.
            6. LIBERTAD DE CONSULTA Y CONOCIMIENTO GENERAL UNIVERSAL: Tienes total libertad para ayudar al comerciante en CUALQUIER rubro o tipo de negocio (ej. tiendas de peces y acuarios sobre compatibilidad de especies y alimentación; restaurantes y pastelerías sobre recetas e ingredientes; talleres mecánicos sobre compatibilidad de repuestos; farmacias y tiendas de medicamentos sobre componentes y principios activos; ferreterías, ropa, librerías, etc.), además de especificaciones técnicas, electrónica, consejos comerciales, financieros, tributarios y normativas SENIAT. Ayuda al usuario con cualquier duda experta que tenga para que su negocio nunca necesite salir de la aplicación.
            7. REGLA ESTRICTA DE IDIOMA Y EXTENSIÓN: Responde SIEMPRE Y EXCLUSIVAMENTE EN ESPAÑOL. NUNCA respondas en inglés. En modo Live / voz (y chat), da respuestas CLARAS, DIRECTAS Y PUNTUALES sin relleno innecesario para que el flujo de trabajo sea rápido y cómodo.
        """.trimIndent()
    }

    fun getToolDeclarationsJson(): JSONArray {
        val arr = JSONArray()
        functionDeclarations.forEach { decl ->
            val fnJson = JSONObject().apply {
                put("name", decl.name)
                put("description", decl.description)
                decl.parameters?.let { params ->
                    put("parameters", functionParametersToJson(params))
                }
            }
            arr.put(fnJson)
        }
        return arr
    }

    private fun functionParametersToJson(params: FunctionParameters): JSONObject {
        val obj = JSONObject()
        obj.put("type", params.type)
        val propsObj = JSONObject()
        params.properties?.forEach { (k, schema) ->
            propsObj.put(k, propertySchemaToJson(schema))
        }
        obj.put("properties", propsObj)
        if (!params.required.isNullOrEmpty()) {
            obj.put("required", JSONArray(params.required))
        }
        return obj
    }

    private fun propertySchemaToJson(schema: PropertySchema): JSONObject {
        val obj = JSONObject()
        obj.put("type", schema.type)
        schema.description?.let { obj.put("description", it) }
        schema.items?.let { obj.put("items", propertySchemaToJson(it)) }
        schema.properties?.let { nestedProps ->
            val nestedObj = JSONObject()
            nestedProps.forEach { (k, v) -> nestedObj.put(k, propertySchemaToJson(v)) }
            obj.put("properties", nestedObj)
        }
        if (!schema.required.isNullOrEmpty()) {
            obj.put("required", JSONArray(schema.required))
        }
        return obj
    }

    suspend fun processAssistantPrompt(
        userPrompt: String,
        catalogSummary: String,
        suppliersSummary: String,
        customersSummary: String = "",
        bcvRate: Double,
        todaySalesSummary: String = "",
        minProfitMarginPercent: Double? = null,
        pendingActionSummary: String? = null,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        onReadOnlyToolCall: (suspend (name: String, args: Map<String, Any>) -> Map<String, Any>?)? = null
    ): AssistantApiResponse = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKeyOrNull()
            ?: return@withContext AssistantApiResponse.Error("No se ha configurado ninguna clave API de Gemini válida. Puedes ingresar una en Ajustes.")

        val model = getEffectiveModel()
        val systemInstructionText = buildSystemInstruction(
            catalogSummary = catalogSummary,
            suppliersSummary = suppliersSummary,
            customersSummary = customersSummary,
            bcvRate = bcvRate,
            todaySalesSummary = todaySalesSummary,
            minProfitMarginPercent = minProfitMarginPercent,
            pendingActionSummary = pendingActionSummary
        )

        val contents = mutableListOf<Content>()
        conversationHistory.takeLast(8).forEach { (role, text) ->
            if (text.isNotBlank()) {
                contents.add(Content(role = if (role == "USER") "user" else "model", parts = listOf(Part(text = text))))
            }
        }
        if (contents.lastOrNull()?.role != "user" || contents.lastOrNull()?.parts?.firstOrNull()?.text != userPrompt) {
            contents.add(Content(role = "user", parts = listOf(Part(text = userPrompt))))
        }

        try {
            // Up to 3 reasoning/tool-response steps for read-only lookups
            repeat(3) {
                val request = GenerateContentRequest(
                    contents = contents,
                    systemInstruction = Content(role = "system", parts = listOf(Part(text = systemInstructionText))),
                    tools = tools
                )
                val response = service.generateContent(
                    model = model,
                    apiKey = apiKey,
                    request = request
                )
                val candidate = response.candidates?.firstOrNull()
                val parts = candidate?.content?.parts.orEmpty()
                val functionCallPart = parts.firstOrNull { it.functionCall != null }

                if (functionCallPart?.functionCall != null) {
                    val call = functionCallPart.functionCall
                    val args = call.args ?: emptyMap()
                    val readResult = onReadOnlyToolCall?.invoke(call.name, args)
                    if (readResult != null) {
                        contents.add(Content(role = "model", parts = listOf(Part(functionCall = call))))
                        contents.add(
                            Content(
                                role = "user",
                                parts = listOf(
                                    Part(
                                        functionResponse = FunctionResponse(
                                            name = call.name,
                                            response = readResult
                                        )
                                    )
                                )
                            )
                        )
                    } else {
                        return@withContext AssistantApiResponse.Function(call.name, args)
                    }
                } else {
                    val reply = parts.mapNotNull { it.text }.joinToString("\n").trim()
                    if (reply.isBlank()) {
                        return@withContext AssistantApiResponse.Message("No entendí tu solicitud, puedes repetir tu solicitud.")
                    }
                    return@withContext AssistantApiResponse.Message(reply)
                }
            }
            return@withContext AssistantApiResponse.Message("No entendí tu solicitud, puedes repetir tu solicitud.")
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = e.localizedMessage ?: "Error desconocido"
            return@withContext AssistantApiResponse.Error("Error al conectar con Gemini ($model): $msg")
        }
    }

    suspend fun testGeminiConnection(apiKey: String, model: String): Result<String> = withContext(Dispatchers.IO) {
        val keyToTest = apiKey.ifBlank { BuildConfig.GEMINI_API_KEY.trim() }
        if (keyToTest.isBlank() || keyToTest.equals("demo", ignoreCase = true)) {
            return@withContext Result.failure(Exception("Clave API de Gemini vacía o de demostración."))
        }

        val request = GenerateContentRequest(
            contents = listOf(
                Content(role = "user", parts = listOf(Part(text = "Di exactamente 'OK_GEMINI_ONLINE' si estás activo.")))
            )
        )

        try {
            val response = service.generateContent(
                model = model.ifBlank { "gemini-3.5-flash-lite" },
                apiKey = keyToTest,
                request = request
            )
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text.orEmpty()
            if (text.isNotBlank()) {
                Result.success("Conexión exitosa con $model.")
            } else {
                Result.failure(Exception("Respuesta vacía de Gemini."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractPurchaseInvoiceFromImage(
        imageBase64: String,
        ocrRawText: String,
        suppliersSummary: String,
        catalogSummary: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKeyOrNull()
            ?: return@withContext Result.failure(Exception("No hay clave API de Gemini configurada."))

        val model = getEffectiveModel()
        val prompt = """
            Analiza la imagen de esta factura de compra y el texto OCR extraído de apoyo.
            Extrae ÚNICAMENTE un objeto JSON válido (sin markdown, sin texto adicional) con esta estructura exacta:
            {
              "supplierName": "Nombre o razón social del proveedor",
              "invoiceNumber": "Número de factura o control",
              "currency": "USD o VES (según estén impresos los precios en la factura: $ / USD = USD, Bs / Bs.S / VES = VES)",
              "subtotalAmount": 0.00,
              "hasIva": false,
              "ivaPercentage": 16.0,
              "ivaAmount": 0.00,
              "totalAmountUsd": 0.00,
              "items": [
                {
                  "name": "Nombre limpio del producto",
                  "quantity": 1.0,
                  "unitCostUsd": 0.00,
                  "lineTotalUsd": 0.00,
                  "isTaxExempt": false
                }
              ]
            }

            REGLAS:
            1. NO conviertas monedas: devuelve los montos unitCostUsd, lineTotalUsd, subtotalAmount, ivaAmount y totalAmountUsd exactamente en la moneda impresa en la factura ('USD' o 'VES') e indica correctamente el campo "currency".
            2. Si la factura muestra IVA desglosado (ej. IVA 16% o IVA 8%), pon "hasIva": true, su porcentaje en "ivaPercentage" y el monto en "ivaAmount". Si algún producto tiene marca (E) o Exento, pon "isTaxExempt": true.
            3. Si el nombre del producto coincide con alguno del catálogo actual, usa preferiblemente el nombre exacto del catálogo para facilitar la suma automática al inventario.
            4. Si el proveedor coincide con uno registrado, usa su nombre exacto.
            5. Todos los montos numéricos deben ser números decimales positivos. No inventes productos que no estén en la factura.

            PROVEEDORES REGISTRADOS:
            $suppliersSummary

            CATÁLOGO ACTUAL (muestra):
            $catalogSummary

            TEXTO OCR DETECTADO EN DISPOSITIVO:
            $ocrRawText
        """.trimIndent()

        val parts = mutableListOf(Part(text = prompt))
        if (imageBase64.isNotBlank()) {
            parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = imageBase64)))
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(role = "user", parts = parts))
        )

        try {
            val response = service.generateContent(
                model = model,
                apiKey = apiKey,
                request = request
            )
            val text = response.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }
                ?.joinToString("\n")
                ?.trim()
                .orEmpty()
            if (text.isNotBlank()) {
                Result.success(text)
            } else {
                Result.failure(Exception("Gemini devolvió una respuesta vacía al analizar la factura."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractPosTicketFromImage(
        imageBase64: String,
        ocrRawText: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKeyOrNull()
            ?: return@withContext Result.failure(Exception("No hay clave API de Gemini configurada."))

        val model = getEffectiveModel()
        val prompt = """
            Analiza la imagen de este ticket/voucher de punto de venta (POS bancario) y el texto OCR de apoyo.
            Extrae ÚNICAMENTE un objeto JSON válido (sin markdown, sin texto adicional) con esta estructura exacta:
            {
              "transactionDateTime": "dd/MM/yyyy HH:mm (fecha y hora impresa en el ticket)",
              "receiptNumber": "Número de recibo o ticket (ej. 000123)",
              "referenceNumber": "Número de referencia / trace / STAN (ej. 004512)",
              "approvalNumber": "Número de aprobación / autorización (ej. 849201)",
              "maskedCardNumber": "Número de tarjeta enmascarado tal como aparece (ej. 4512********8901 o **** 8901)",
              "operationType": "Tipo de operación (COMPRA, COMPRA DÉBITO, COMPRA CRÉDITO, ANULACIÓN, DEVOLUCIÓN)",
              "totalAmount": 0.00,
              "currency": "VES o USD (VES si está en Bs./Bs.S/Bolívares, USD si está en $ o Dólares)",
              "transactionStatus": "APROBADA, RECHAZADA, ANULADA o PENDIENTE",
              "bankOrTerminal": "Nombre del banco / adquirente o ID del terminal si aparece"
            }

            REGLAS:
            1. Si algún campo no es visible en el ticket, devuelve cadena vacía "" (o 0.0 para totalAmount).
            2. En "maskedCardNumber", conserva el enmascaramiento y los últimos 4 dígitos visibles.
            3. En "totalAmount", devuelve únicamente el número decimal positivo del monto total.
            4. En "transactionStatus", normaliza estrictamente a uno de: APROBADA, RECHAZADA, ANULADA, PENDIENTE.

            TEXTO OCR DETECTADO EN DISPOSITIVO:
            $ocrRawText
        """.trimIndent()

        val parts = mutableListOf(Part(text = prompt))
        if (imageBase64.isNotBlank()) {
            parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = imageBase64)))
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(role = "user", parts = parts))
        )

        try {
            val response = service.generateContent(
                model = model,
                apiKey = apiKey,
                request = request
            )
            val text = response.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }
                ?.joinToString("\n")
                ?.trim()
                .orEmpty()
            if (text.isNotBlank()) {
                Result.success(text)
            } else {
                Result.failure(Exception("Gemini devolvió una respuesta vacía al analizar el ticket."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun smartOrganizeInventoryProducts(
        productsJsonList: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKeyOrNull()
            ?: return@withContext Result.failure(Exception("No se ha configurado ninguna clave API de Gemini válida."))
        val model = getEffectiveModel()

        val prompt = """
            Eres un experto en gestión de inventarios y organización comercial para cualquier tipo de negocio (comercio, ferretería, farmacia, restaurante, tienda de mascotas, taller, etc.).
            Analiza la siguiente lista JSON de productos actuales del inventario.
            Para CADA producto en la lista, debes:
            1. Asignarle una CATEGORÍA comercial profesional, limpia y adecuada basada en su nombre (ej. "Víveres", "Bebidas", "Limpieza", "Electrónica", "Acuario", "Repuestos", "Medicamentos", "Lácteos", "Carnes", "Herramientas", "Alimentos", etc.).
            2. Si el producto no tiene código (code está vacío, en blanco o es "S/N"), generarle un CÓDIGO ÚNICO alfanumérico limpio y profesional (ej. "PRD-001", "CAT-05", etc.). Si ya tiene un código válido, consérvalo.

            Devuelve la respuesta ÚNICAMENTE en formato JSON válido como un arreglo de objetos JSON con esta estructura exacta (sin texto adicional ni formato markdown):
            [
              {
                "id": 1,
                "category": "Víveres",
                "code": "HAR-001"
              },
              {
                "id": 2,
                "category": "Bebidas",
                "code": "REF-002"
              }
            ]

            Lista de productos actuales:
            $productsJsonList
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(role = "user", parts = listOf(Part(text = prompt)))
            ),
            systemInstruction = Content(role = "system", parts = listOf(Part(text = "Eres un asistente experto en organización de inventarios. Devuelve estrictamente JSON válido.")))
        )

        try {
            val response = service.generateContent(
                model = model,
                apiKey = apiKey,
                request = request
            )
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text.orEmpty()
            if (text.isNotBlank()) {
                Result.success(text)
            } else {
                Result.failure(Exception("Respuesta vacía de IA."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

sealed class AssistantApiResponse {
    data class Message(val text: String) : AssistantApiResponse()
    data class Function(val functionName: String, val args: Map<String, Any>) : AssistantApiResponse()
    data class Error(val errorMessage: String) : AssistantApiResponse()
}
