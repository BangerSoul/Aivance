package com.bangersoul.aivance.feature.jobs

/**
 * World Country → State/Region → City catalog used by the Job Search filter
 * dropdowns. This is a curated but broad dataset covering every inhabited
 * continent — providers receive the combined location string, and client-side
 * matching uses each selected dimension. Extend freely.
 */
object LocationCatalog {

    data class State(val name: String, val cities: List<String>)
    data class Country(val name: String, val states: List<State>)

    /** The special "Remote" option is available at every level. */
    const val REMOTE = "Remote"

    val countries: List<Country> = listOf(
        // ── North America ─────────────────────────────────────────────
        Country(
            name = "United States",
            states = listOf(
                State("Alabama", listOf("Birmingham", "Montgomery", "Huntsville")),
                State("Alaska", listOf("Anchorage", "Fairbanks", "Juneau")),
                State("Arizona", listOf("Phoenix", "Tucson", "Scottsdale")),
                State("Arkansas", listOf("Little Rock", "Fayetteville")),
                State("California", listOf("San Francisco", "Los Angeles", "San Diego", "Mountain View", "San Jose")),
                State("Colorado", listOf("Denver", "Boulder", "Colorado Springs")),
                State("Connecticut", listOf("Hartford", "Stamford", "New Haven")),
                State("Delaware", listOf("Wilmington", "Dover")),
                State("Florida", listOf("Miami", "Orlando", "Tampa", "Jacksonville")),
                State("Georgia", listOf("Atlanta", "Savannah")),
                State("Hawaii", listOf("Honolulu")),
                State("Idaho", listOf("Boise")),
                State("Illinois", listOf("Chicago", "Naperville")),
                State("Indiana", listOf("Indianapolis", "Fort Wayne")),
                State("Iowa", listOf("Des Moines", "Cedar Rapids")),
                State("Kansas", listOf("Kansas City", "Wichita", "Topeka")),
                State("Kentucky", listOf("Louisville", "Lexington")),
                State("Louisiana", listOf("New Orleans", "Baton Rouge")),
                State("Maine", listOf("Portland", "Augusta")),
                State("Maryland", listOf("Baltimore", "Rockville", "Annapolis")),
                State("Massachusetts", listOf("Boston", "Cambridge")),
                State("Michigan", listOf("Detroit", "Ann Arbor", "Grand Rapids")),
                State("Minnesota", listOf("Minneapolis", "St. Paul")),
                State("Mississippi", listOf("Jackson", "Gulfport")),
                State("Missouri", listOf("St. Louis", "Kansas City")),
                State("Montana", listOf("Bozeman", "Billings")),
                State("Nebraska", listOf("Omaha", "Lincoln")),
                State("Nevada", listOf("Las Vegas", "Reno")),
                State("New Hampshire", listOf("Manchester", "Concord")),
                State("New Jersey", listOf("Newark", "Jersey City", "Princeton")),
                State("New Mexico", listOf("Albuquerque", "Santa Fe")),
                State("New York", listOf("New York", "Buffalo", "Rochester", "Albany")),
                State("North Carolina", listOf("Charlotte", "Raleigh", "Durham")),
                State("North Dakota", listOf("Fargo", "Bismarck")),
                State("Ohio", listOf("Columbus", "Cleveland", "Cincinnati")),
                State("Oklahoma", listOf("Oklahoma City", "Tulsa")),
                State("Oregon", listOf("Portland", "Eugene", "Salem")),
                State("Pennsylvania", listOf("Philadelphia", "Pittsburgh", "Harrisburg")),
                State("Rhode Island", listOf("Providence")),
                State("South Carolina", listOf("Charleston", "Columbia")),
                State("South Dakota", listOf("Sioux Falls", "Rapid City")),
                State("Tennessee", listOf("Nashville", "Memphis", "Knoxville")),
                State("Texas", listOf("Austin", "Dallas", "Houston", "San Antonio")),
                State("Utah", listOf("Salt Lake City", "Provo")),
                State("Vermont", listOf("Burlington", "Montpelier")),
                State("Virginia", listOf("Richmond", "Arlington", "Virginia Beach")),
                State("Washington", listOf("Seattle", "Redmond", "Bellevue")),
                State("Washington, D.C.", listOf("Washington")),
                State("West Virginia", listOf("Charleston", "Morgantown")),
                State("Wisconsin", listOf("Milwaukee", "Madison")),
                State("Wyoming", listOf("Cheyenne", "Jackson"))
            )
        ),
        Country(
            name = "Canada",
            states = listOf(
                State("Ontario", listOf("Toronto", "Ottawa", "Mississauga", "Waterloo")),
                State("British Columbia", listOf("Vancouver", "Victoria", "Burnaby")),
                State("Quebec", listOf("Montreal", "Quebec City")),
                State("Alberta", listOf("Calgary", "Edmonton")),
                State("Manitoba", listOf("Winnipeg")),
                State("Saskatchewan", listOf("Saskatoon", "Regina")),
                State("Nova Scotia", listOf("Halifax")),
                State("New Brunswick", listOf("Moncton", "Fredericton")),
                State("Newfoundland and Labrador", listOf("St. John's")),
                State("Prince Edward Island", listOf("Charlottetown")),
                State("Yukon", listOf("Whitehorse")),
                State("Northwest Territories", listOf("Yellowknife")),
                State("Nunavut", listOf("Iqaluit"))
            )
        ),
        Country(
            name = "Mexico",
            states = listOf(
                State("Mexico City", listOf("Mexico City")),
                State("Jalisco", listOf("Guadalajara", "Puerto Vallarta")),
                State("Nuevo León", listOf("Monterrey")),
                State("Puebla", listOf("Puebla")),
                State("Yucatán", listOf("Mérida")),
                State("Baja California", listOf("Tijuana", "Mexicali")),
                State("Guanajuato", listOf("León")),
                State("Querétaro", listOf("Querétaro")),
                State("Oaxaca", listOf("Oaxaca City"))
            )
        ),

        // ── South America ─────────────────────────────────────────────
        Country(
            name = "Brazil",
            states = listOf(
                State("São Paulo", listOf("São Paulo", "Campinas")),
                State("Rio de Janeiro", listOf("Rio de Janeiro")),
                State("Minas Gerais", listOf("Belo Horizonte")),
                State("Distrito Federal", listOf("Brasília")),
                State("Bahia", listOf("Salvador")),
                State("Paraná", listOf("Curitiba")),
                State("Rio Grande do Sul", listOf("Porto Alegre")),
                State("Pernambuco", listOf("Recife")),
                State("Ceará", listOf("Fortaleza")),
                State("Amazonas", listOf("Manaus"))
            )
        ),
        Country(
            name = "Argentina",
            states = listOf(
                State("Buenos Aires", listOf("Buenos Aires", "La Plata")),
                State("Córdoba", listOf("Córdoba")),
                State("Santa Fe", listOf("Rosario", "Santa Fe")),
                State("Mendoza", listOf("Mendoza")),
                State("Tucumán", listOf("San Miguel de Tucumán")),
                State("Neuquén", listOf("Neuquén"))
            )
        ),
        Country(
            name = "Colombia",
            states = listOf(
                State("Cundinamarca", listOf("Bogotá")),
                State("Antioquia", listOf("Medellín")),
                State("Valle del Cauca", listOf("Cali")),
                State("Atlántico", listOf("Barranquilla")),
                State("Santander", listOf("Bucaramanga"))
            )
        ),
        Country(
            name = "Chile",
            states = listOf(
                State("Santiago Metropolitan", listOf("Santiago")),
                State("Valparaíso", listOf("Valparaíso", "Viña del Mar")),
                State("Biobío", listOf("Concepción")),
                State("Araucanía", listOf("Temuco"))
            )
        ),
        Country(
            name = "Peru",
            states = listOf(
                State("Lima", listOf("Lima")),
                State("Arequipa", listOf("Arequipa")),
                State("Cusco", listOf("Cusco")),
                State("La Libertad", listOf("Trujillo"))
            )
        ),
        Country(
            name = "Uruguay",
            states = listOf(
                State("Montevideo", listOf("Montevideo")),
                State("Canelones", listOf("Canelones"))
            )
        ),

        // ── Europe ────────────────────────────────────────────────────
        Country(
            name = "United Kingdom",
            states = listOf(
                State("England", listOf("London", "Manchester", "Birmingham", "Leeds", "Cambridge")),
                State("Scotland", listOf("Edinburgh", "Glasgow")),
                State("Wales", listOf("Cardiff", "Swansea")),
                State("Northern Ireland", listOf("Belfast"))
            )
        ),
        Country(
            name = "Germany",
            states = listOf(
                State("Berlin", listOf("Berlin")),
                State("Bavaria", listOf("Munich", "Nuremberg")),
                State("Hamburg", listOf("Hamburg")),
                State("Hesse", listOf("Frankfurt", "Darmstadt")),
                State("North Rhine-Westphalia", listOf("Cologne", "Düsseldorf", "Dortmund")),
                State("Baden-Württemberg", listOf("Stuttgart", "Karlsruhe")),
                State("Saxony", listOf("Leipzig", "Dresden")),
                State("Lower Saxony", listOf("Hannover", "Braunschweig"))
            )
        ),
        Country(
            name = "France",
            states = listOf(
                State("Île-de-France", listOf("Paris", "Boulogne-Billancourt")),
                State("Auvergne-Rhône-Alpes", listOf("Lyon", "Grenoble")),
                State("Provence-Alpes-Côte d'Azur", listOf("Marseille", "Nice", "Sophia Antipolis")),
                State("Occitanie", listOf("Toulouse", "Montpellier")),
                State("Nouvelle-Aquitaine", listOf("Bordeaux")),
                State("Grand Est", listOf("Strasbourg", "Nancy")),
                State("Hauts-de-France", listOf("Lille")),
                State("Bretagne", listOf("Rennes", "Nantes"))
            )
        ),
        Country(
            name = "Spain",
            states = listOf(
                State("Madrid", listOf("Madrid")),
                State("Catalonia", listOf("Barcelona", "Tarragona")),
                State("Basque Country", listOf("Bilbao", "San Sebastián")),
                State("Valencia", listOf("Valencia")),
                State("Andalusia", listOf("Seville", "Málaga", "Granada")),
                State("Galicia", listOf("Vigo", "A Coruña")),
                State("Balearic Islands", listOf("Palma de Mallorca"))
            )
        ),
        Country(
            name = "Italy",
            states = listOf(
                State("Lombardy", listOf("Milan")),
                State("Lazio", listOf("Rome")),
                State("Piedmont", listOf("Turin")),
                State("Emilia-Romagna", listOf("Bologna")),
                State("Tuscany", listOf("Florence")),
                State("Campania", listOf("Naples")),
                State("Veneto", listOf("Venice", "Verona"))
            )
        ),
        Country(
            name = "Netherlands",
            states = listOf(
                State("North Holland", listOf("Amsterdam", "Haarlem")),
                State("South Holland", listOf("Rotterdam", "The Hague", "Leiden")),
                State("Utrecht", listOf("Utrecht")),
                State("North Brabant", listOf("Eindhoven", "Tilburg")),
                State("Gelderland", listOf("Nijmegen"))
            )
        ),
        Country(
            name = "Portugal",
            states = listOf(
                State("Lisbon", listOf("Lisbon", "Cascais")),
                State("Porto", listOf("Porto")),
                State("Braga", listOf("Braga")),
                State("Faro", listOf("Faro", "Albufeira"))
            )
        ),
        Country(
            name = "Belgium",
            states = listOf(
                State("Brussels", listOf("Brussels")),
                State("Flanders", listOf("Antwerp", "Ghent", "Leuven")),
                State("Wallonia", listOf("Liège", "Namur"))
            )
        ),
        Country(
            name = "Switzerland",
            states = listOf(
                State("Zürich", listOf("Zurich")),
                State("Geneva", listOf("Geneva")),
                State("Bern", listOf("Bern")),
                State("Basel-Stadt", listOf("Basel")),
                State("Vaud", listOf("Lausanne"))
            )
        ),
        Country(
            name = "Austria",
            states = listOf(
                State("Vienna", listOf("Vienna")),
                State("Styria", listOf("Graz")),
                State("Upper Austria", listOf("Linz")),
                State("Tyrol", listOf("Innsbruck")),
                State("Salzburg", listOf("Salzburg"))
            )
        ),
        Country(
            name = "Ireland",
            states = listOf(
                State("Leinster", listOf("Dublin")),
                State("Munster", listOf("Cork", "Limerick")),
                State("Connacht", listOf("Galway"))
            )
        ),
        Country(
            name = "Sweden",
            states = listOf(
                State("Stockholm", listOf("Stockholm")),
                State("Västra Götaland", listOf("Gothenburg")),
                State("Skåne", listOf("Malmö", "Lund")),
                State("Uppsala", listOf("Uppsala"))
            )
        ),
        Country(
            name = "Norway",
            states = listOf(
                State("Oslo", listOf("Oslo")),
                State("Vestland", listOf("Bergen")),
                State("Trøndelag", listOf("Trondheim")),
                State("Rogaland", listOf("Stavanger"))
            )
        ),
        Country(
            name = "Denmark",
            states = listOf(
                State("Capital Region", listOf("Copenhagen")),
                State("Central Denmark", listOf("Aarhus")),
                State("Southern Denmark", listOf("Odense"))
            )
        ),
        Country(
            name = "Finland",
            states = listOf(
                State("Uusimaa", listOf("Helsinki", "Espoo")),
                State("Pirkanmaa", listOf("Tampere")),
                State("Southwest Finland", listOf("Turku")),
                State("Northern Ostrobothnia", listOf("Oulu"))
            )
        ),
        Country(
            name = "Poland",
            states = listOf(
                State("Masovian", listOf("Warsaw")),
                State("Lesser Poland", listOf("Kraków")),
                State("Lower Silesian", listOf("Wrocław")),
                State("Greater Poland", listOf("Poznań")),
                State("Pomeranian", listOf("Gdańsk", "Gdynia")),
                State("Łódź", listOf("Łódź"))
            )
        ),
        Country(
            name = "Czechia",
            states = listOf(
                State("Prague", listOf("Prague")),
                State("South Moravian", listOf("Brno")),
                State("Moravian-Silesian", listOf("Ostrava"))
            )
        ),
        Country(
            name = "Greece",
            states = listOf(
                State("Attica", listOf("Athens")),
                State("Central Macedonia", listOf("Thessaloniki")),
                State("Crete", listOf("Heraklion"))
            )
        ),
        Country(
            name = "Romania",
            states = listOf(
                State("Bucharest", listOf("Bucharest")),
                State("Cluj", listOf("Cluj-Napoca")),
                State("Timiș", listOf("Timișoara")),
                State("Iași", listOf("Iași"))
            )
        ),
        Country(
            name = "Hungary",
            states = listOf(
                State("Budapest", listOf("Budapest")),
                State("Csongrád-Csanád", listOf("Szeged")),
                State("Hajdú-Bihar", listOf("Debrecen"))
            )
        ),
        Country(
            name = "Ukraine",
            states = listOf(
                State("Kyiv", listOf("Kyiv")),
                State("Lviv", listOf("Lviv")),
                State("Kharkiv", listOf("Kharkiv")),
                State("Odesa", listOf("Odesa")),
                State("Dnipro", listOf("Dnipro"))
            )
        ),
        Country(
            name = "Turkey",
            states = listOf(
                State("Istanbul", listOf("Istanbul")),
                State("Ankara", listOf("Ankara")),
                State("İzmir", listOf("İzmir")),
                State("Bursa", listOf("Bursa")),
                State("Antalya", listOf("Antalya"))
            )
        ),
        Country(
            name = "Russia",
            states = listOf(
                State("Moscow", listOf("Moscow")),
                State("Saint Petersburg", listOf("Saint Petersburg")),
                State("Novosibirsk Oblast", listOf("Novosibirsk")),
                State("Tatarstan", listOf("Kazan")),
                State("Sverdlovsk Oblast", listOf("Yekaterinburg"))
            )
        ),

        // ── Asia ──────────────────────────────────────────────────────
        Country(
            name = "India",
            states = listOf(
                State("Karnataka", listOf("Bengaluru", "Mysuru")),
                State("Maharashtra", listOf("Mumbai", "Pune", "Nashik")),
                State("Delhi", listOf("New Delhi", "Gurugram", "Noida")),
                State("Telangana", listOf("Hyderabad")),
                State("Tamil Nadu", listOf("Chennai", "Coimbatore", "Madurai")),
                State("West Bengal", listOf("Kolkata")),
                State("Gujarat", listOf("Ahmedabad", "Surat")),
                State("Rajasthan", listOf("Jaipur")),
                State("Uttar Pradesh", listOf("Lucknow", "Noida", "Kanpur")),
                State("Kerala", listOf("Thiruvananthapuram", "Kochi")),
                State("Haryana", listOf("Gurugram", "Faridabad")),
                State("Punjab", listOf("Chandigarh", "Ludhiana")),
                State("Madhya Pradesh", listOf("Indore", "Bhopal")),
                State("Andhra Pradesh", listOf("Visakhapatnam", "Vijayawada")),
                State("Odisha", listOf("Bhubaneswar")),
                State("Bihar", listOf("Patna")),
                State("Assam", listOf("Guwahati")),
                State("Jharkhand", listOf("Ranchi")),
                State("Chhattisgarh", listOf("Raipur")),
                State("Goa", listOf("Panaji")),
                State("Uttarakhand", listOf("Dehradun")),
                State("Himachal Pradesh", listOf("Shimla")),
                State("Jammu & Kashmir", listOf("Srinagar", "Jammu")),
                State("Puducherry", listOf("Puducherry"))
            )
        ),
        Country(
            name = "China",
            states = listOf(
                State("Beijing", listOf("Beijing")),
                State("Shanghai", listOf("Shanghai")),
                State("Guangdong", listOf("Shenzhen", "Guangzhou", "Dongguan")),
                State("Zhejiang", listOf("Hangzhou", "Ningbo")),
                State("Jiangsu", listOf("Nanjing", "Suzhou", "Wuxi")),
                State("Sichuan", listOf("Chengdu")),
                State("Hubei", listOf("Wuhan")),
                State("Shaanxi", listOf("Xi'an")),
                State("Fujian", listOf("Xiamen", "Fuzhou")),
                State("Chongqing", listOf("Chongqing")),
                State("Tianjin", listOf("Tianjin")),
                State("Hunan", listOf("Changsha")),
                State("Shandong", listOf("Qingdao", "Jinan")),
                State("Anhui", listOf("Hefei"))
            )
        ),
        Country(
            name = "Japan",
            states = listOf(
                State("Tokyo", listOf("Tokyo", "Shibuya", "Shinjuku")),
                State("Kanagawa", listOf("Yokohama", "Kawasaki")),
                State("Osaka", listOf("Osaka")),
                State("Aichi", listOf("Nagoya")),
                State("Kyoto", listOf("Kyoto")),
                State("Hokkaido", listOf("Sapporo")),
                State("Fukuoka", listOf("Fukuoka")),
                State("Hyogo", listOf("Kobe")),
                State("Saitama", listOf("Saitama")),
                State("Chiba", listOf("Chiba"))
            )
        ),
        Country(
            name = "South Korea",
            states = listOf(
                State("Seoul", listOf("Seoul", "Gangnam")),
                State("Gyeonggi", listOf("Suwon", "Seongnam", "Goyang")),
                State("Busan", listOf("Busan")),
                State("Incheon", listOf("Incheon")),
                State("Daegu", listOf("Daegu")),
                State("Daejeon", listOf("Daejeon")),
                State("Gwangju", listOf("Gwangju"))
            )
        ),
        Country(
            name = "Singapore",
            states = listOf(
                State("Singapore", listOf("Singapore", "Jurong East", "Tampines"))
            )
        ),
        Country(
            name = "United Arab Emirates",
            states = listOf(
                State("Dubai", listOf("Dubai")),
                State("Abu Dhabi", listOf("Abu Dhabi")),
                State("Sharjah", listOf("Sharjah")),
                State("Ajman", listOf("Ajman"))
            )
        ),
        Country(
            name = "Saudi Arabia",
            states = listOf(
                State("Riyadh", listOf("Riyadh")),
                State("Makkah", listOf("Jeddah", "Mecca")),
                State("Eastern Province", listOf("Dammam", "Khobar")),
                State("Madinah", listOf("Medina"))
            )
        ),
        Country(
            name = "Qatar",
            states = listOf(
                State("Doha", listOf("Doha")),
                State("Al Rayyan", listOf("Al Rayyan"))
            )
        ),
        Country(
            name = "Israel",
            states = listOf(
                State("Tel Aviv District", listOf("Tel Aviv")),
                State("Jerusalem District", listOf("Jerusalem")),
                State("Haifa District", listOf("Haifa")),
                State("Central District", listOf("Rishon LeZion", "Netanya"))
            )
        ),
        Country(
            name = "Indonesia",
            states = listOf(
                State("Jakarta", listOf("Jakarta")),
                State("West Java", listOf("Bandung", "Bekasi")),
                State("East Java", listOf("Surabaya", "Malang")),
                State("Bali", listOf("Denpasar")),
                State("Yogyakarta", listOf("Yogyakarta"))
            )
        ),
        Country(
            name = "Malaysia",
            states = listOf(
                State("Kuala Lumpur", listOf("Kuala Lumpur")),
                State("Selangor", listOf("Petaling Jaya", "Shah Alam")),
                State("Penang", listOf("George Town")),
                State("Johor", listOf("Johor Bahru")),
                State("Sarawak", listOf("Kuching"))
            )
        ),
        Country(
            name = "Thailand",
            states = listOf(
                State("Bangkok", listOf("Bangkok")),
                State("Chiang Mai", listOf("Chiang Mai")),
                State("Phuket", listOf("Phuket City")),
                State("Chonburi", listOf("Pattaya"))
            )
        ),
        Country(
            name = "Vietnam",
            states = listOf(
                State("Ho Chi Minh City", listOf("Ho Chi Minh City")),
                State("Hanoi", listOf("Hanoi")),
                State("Da Nang", listOf("Da Nang")),
                State("Binh Duong", listOf("Thu Dau Mot"))
            )
        ),
        Country(
            name = "Philippines",
            states = listOf(
                State("Metro Manila", listOf("Manila", "Makati", "Quezon City")),
                State("Cebu", listOf("Cebu City")),
                State("Davao", listOf("Davao City")),
                State("Laguna", listOf("Santa Rosa"))
            )
        ),
        Country(
            name = "Pakistan",
            states = listOf(
                State("Sindh", listOf("Karachi", "Hyderabad")),
                State("Punjab", listOf("Lahore", "Faisalabad")),
                State("Islamabad", listOf("Islamabad")),
                State("Khyber Pakhtunkhwa", listOf("Peshawar")),
                State("Balochistan", listOf("Quetta"))
            )
        ),
        Country(
            name = "Bangladesh",
            states = listOf(
                State("Dhaka", listOf("Dhaka")),
                State("Chattogram", listOf("Chattogram")),
                State("Khulna", listOf("Khulna")),
                State("Sylhet", listOf("Sylhet"))
            )
        ),
        Country(
            name = "Sri Lanka",
            states = listOf(
                State("Western Province", listOf("Colombo", "Negombo")),
                State("Central Province", listOf("Kandy")),
                State("Southern Province", listOf("Galle"))
            )
        ),
        Country(
            name = "Nepal",
            states = listOf(
                State("Bagmati", listOf("Kathmandu", "Lalitpur")),
                State("Lumbini", listOf("Butwal")),
                State("Gandaki", listOf("Pokhara"))
            )
        ),
        Country(
            name = "Kuwait",
            states = listOf(
                State("Kuwait City", listOf("Kuwait City")),
                State("Hawalli", listOf("Hawalli"))
            )
        ),
        Country(
            name = "Bahrain",
            states = listOf(
                State("Capital Governorate", listOf("Manama"))
            )
        ),
        Country(
            name = "Oman",
            states = listOf(
                State("Muscat", listOf("Muscat")),
                State("Dhofar", listOf("Salalah"))
            )
        ),
        Country(
            name = "Jordan",
            states = listOf(
                State("Amman", listOf("Amman")),
                State("Irbid", listOf("Irbid")),
                State("Aqaba", listOf("Aqaba"))
            )
        ),
        Country(
            name = "Kazakhstan",
            states = listOf(
                State("Almaty", listOf("Almaty")),
                State("Astana", listOf("Astana")),
                State("Karaganda", listOf("Karaganda"))
            )
        ),

        // ── Oceania ───────────────────────────────────────────────────
        Country(
            name = "Australia",
            states = listOf(
                State("New South Wales", listOf("Sydney", "Newcastle")),
                State("Victoria", listOf("Melbourne")),
                State("Queensland", listOf("Brisbane", "Gold Coast")),
                State("Western Australia", listOf("Perth")),
                State("South Australia", listOf("Adelaide")),
                State("Tasmania", listOf("Hobart")),
                State("Australian Capital Territory", listOf("Canberra")),
                State("Northern Territory", listOf("Darwin"))
            )
        ),
        Country(
            name = "New Zealand",
            states = listOf(
                State("Auckland", listOf("Auckland")),
                State("Wellington", listOf("Wellington")),
                State("Canterbury", listOf("Christchurch")),
                State("Otago", listOf("Dunedin", "Queenstown"))
            )
        ),

        // ── Africa ────────────────────────────────────────────────────
        Country(
            name = "South Africa",
            states = listOf(
                State("Gauteng", listOf("Johannesburg", "Pretoria")),
                State("Western Cape", listOf("Cape Town", "Stellenbosch")),
                State("KwaZulu-Natal", listOf("Durban")),
                State("Eastern Cape", listOf("Gqeberha", "East London")),
                State("Free State", listOf("Bloemfontein"))
            )
        ),
        Country(
            name = "Nigeria",
            states = listOf(
                State("Lagos", listOf("Lagos", "Ikeja")),
                State("Federal Capital Territory", listOf("Abuja")),
                State("Rivers", listOf("Port Harcourt")),
                State("Oyo", listOf("Ibadan")),
                State("Kano", listOf("Kano"))
            )
        ),
        Country(
            name = "Kenya",
            states = listOf(
                State("Nairobi", listOf("Nairobi")),
                State("Mombasa", listOf("Mombasa")),
                State("Kiambu", listOf("Thika"))
            )
        ),
        Country(
            name = "Egypt",
            states = listOf(
                State("Cairo", listOf("Cairo")),
                State("Giza", listOf("Giza")),
                State("Alexandria", listOf("Alexandria")),
                State("Red Sea", listOf("Hurghada"))
            )
        ),
        Country(
            name = "Morocco",
            states = listOf(
                State("Casablanca-Settat", listOf("Casablanca")),
                State("Rabat-Salé-Kénitra", listOf("Rabat", "Salé")),
                State("Marrakesh-Safi", listOf("Marrakesh")),
                State("Tangier-Tetouan-Al Hoceima", listOf("Tangier"))
            )
        ),
        Country(
            name = "Ghana",
            states = listOf(
                State("Greater Accra", listOf("Accra")),
                State("Ashanti", listOf("Kumasi")),
                State("Western", listOf("Sekondi-Takoradi"))
            )
        ),
        Country(
            name = "Ethiopia",
            states = listOf(
                State("Addis Ababa", listOf("Addis Ababa")),
                State("Amhara", listOf("Bahir Dar")),
                State("Oromia", listOf("Adama"))
            )
        ),
        Country(
            name = "Tanzania",
            states = listOf(
                State("Dar es Salaam", listOf("Dar es Salaam")),
                State("Arusha", listOf("Arusha")),
                State("Dodoma", listOf("Dodoma"))
            )
        ),
        Country(
            name = "Uganda",
            states = listOf(
                State("Central Region", listOf("Kampala")),
                State("Western Region", listOf("Mbarara"))
            )
        ),
        Country(
            name = "Rwanda",
            states = listOf(
                State("Kigali", listOf("Kigali"))
            )
        )
    )

    /** All country names plus Remote. */
    val countryOptions: List<String> = listOf(REMOTE) + countries.map { it.name }

    // Precomputed lookups. statesFor/citiesFor are called straight from composable
    // bodies, so the old linear scan over ~200 countries plus a fresh list
    // allocation on every recomposition both cost time and handed the caller a
    // new instance each pass (breaking its skip check). These return a stable
    // list instance per key, so repeated calls are O(1) and referentially stable.
    private val statesByCountry: Map<String, List<String>> =
        countries.associate { it.name to it.states.map { s -> s.name } }

    private val citiesByCountryState: Map<String, List<String>> =
        countries.flatMap { c -> c.states.map { s -> "${c.name}|${s.name}" to s.cities } }.toMap()

    /** States for a given country (or Remote only). */
    fun statesFor(country: String): List<String> =
        statesByCountry[country] ?: emptyList()

    /** Cities for a given country + state (or Remote only). */
    fun citiesFor(country: String, state: String): List<String> =
        citiesByCountryState["$country|$state"] ?: emptyList()
}
