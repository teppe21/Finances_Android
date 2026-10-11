package com.teppe21.finances.support

/**
 * Synthetic bank statements in the style of Revolut, OTP, Erste, MBH and Wise exports, used by
 * the categorization harness. Every name, amount and reference is invented. The column layouts
 * imitate the shape of real exports (type columns, separate partner and remark columns,
 * debit/credit columns, card noise in descriptions); they are not copies of real files.
 *
 * Each row carries the classification a person would expect, so the harness can measure both
 * how many rows stay uncategorized and how many are put in the wrong place.
 */
object SyntheticStatements {

    data class Row(val cells: String, val kind: String, val category: String)

    data class Statement(val bank: String, val header: String, val rows: List<Row>) {
        val csv: String get() = (listOf(header) + rows.map { it.cells }).joinToString("\n")
    }

    private fun r(cells: String, kind: String, category: String) = Row(cells, kind, category)

    private const val E = "EXPENSE"
    private const val I = "INCOME"
    private const val T = "TRANSFER"
    private const val P = "PERSONAL_TRANSFER"

    /** Revolut: comma separated, a Type column, State, signed amounts, every currency in one file. */
    val revolut = Statement(
        "Revolut",
        "Type,Product,Started Date,Completed Date,Description,Amount,Fee,Currency,State,Balance",
        listOf(
            r("CARD_PAYMENT,Current,2026-09-01 08:12:01,2026-09-02 10:00:00,Spar,-4590.00,0.00,HUF,COMPLETED,95410.00", E, "food"),
            r("CARD_PAYMENT,Current,2026-09-01 18:40:12,2026-09-02 10:00:00,SPAR 1234 BUDAPEST HU,-2390.00,0.00,HUF,COMPLETED,93020.00", E, "food"),
            r("CARD_PAYMENT,Current,2026-09-02 12:01:00,2026-09-03 10:00:00,Wolt,-6450.00,0.00,HUF,COMPLETED,86570.00", E, "dining"),
            r("EXCHANGE,Current,2026-09-02 13:00:00,2026-09-02 13:00:00,Exchanged to EUR,-98765.00,0.00,HUF,COMPLETED,-12195.00", T, "transfers"),
            r("EXCHANGE,Current,2026-09-02 13:00:00,2026-09-02 13:00:00,Exchanged to EUR,250.00,0.00,EUR,COMPLETED,250.00", T, "transfers"),
            r("TRANSFER,Current,2026-09-03 09:00:00,2026-09-03 09:00:01,Transfer to Példa Anna,-12000.00,0.00,HUF,COMPLETED,10000.00", P, "personal_transfers"),
            r("TRANSFER,Current,2026-09-04 09:00:00,2026-09-04 09:00:01,Transfer to MINTA GABOR,-5000.00,0.00,HUF,COMPLETED,5000.00", P, "personal_transfers"),
            r("TRANSFER,Current,2026-09-05 09:00:00,2026-09-05 09:00:01,Transfer to Molnár Teszt,-4000.00,0.00,HUF,COMPLETED,1000.00", P, "personal_transfers"),
            r("TRANSFER,Current,2026-09-05 19:00:00,2026-09-05 19:00:01,Payment from Minta Gábor,8000.00,0.00,HUF,COMPLETED,9000.00", P, "personal_transfers"),
            r("TOPUP,Current,2026-09-06 07:00:00,2026-09-06 07:00:02,Top-Up by *1234,50000.00,0.00,HUF,COMPLETED,59000.00", T, "transfers"),
            r("TRANSFER,Current,2026-09-06 08:00:00,2026-09-06 08:00:00,Balance migration to another region or legal entity,-1234.56,0.00,HUF,COMPLETED,57765.44", T, "transfers"),
            r("TRANSFER,Current,2026-09-06 08:30:00,2026-09-06 08:30:00,To pocket HUF Holiday,-10000.00,0.00,HUF,COMPLETED,47765.44", T, "transfers"),
            r("CARD_PAYMENT,Current,2026-09-07 10:00:00,2026-09-08 10:00:00,Magyar Államkincstár,-23456.00,0.00,HUF,COMPLETED,24309.44", E, "taxes"),
            r("CARD_PAYMENT,Current,2026-09-07 11:00:00,2026-09-08 10:00:00,EarFun,-15990.00,0.00,HUF,COMPLETED,8319.44", E, "electronics"),
            r("CARD_PAYMENT,Current,2026-09-08 20:00:00,2026-09-09 10:00:00,Netflix.com,-4490.00,0.00,HUF,COMPLETED,3829.44", E, "subscriptions"),
            r("CARD_PAYMENT,Current,2026-09-09 23:10:00,2026-09-10 10:00:00,Uber,-3200.00,0.00,HUF,COMPLETED,629.44", E, "transport"),
            r("CARD_PAYMENT,Current,2026-09-10 19:30:00,2026-09-11 10:00:00,Uber Eats,-5600.00,0.00,HUF,COMPLETED,-4970.56", E, "dining"),
            r("CARD_PAYMENT,Current,2026-09-11 08:00:00,2026-09-12 10:00:00,Bolt,-2100.00,0.00,HUF,COMPLETED,-7070.56", E, "transport"),
            r("CARD_PAYMENT,Current,2026-09-11 19:00:00,2026-09-12 10:00:00,Bolt Food,-4300.00,0.00,HUF,COMPLETED,-11370.56", E, "dining"),
            r("CARD_PAYMENT,Current,2026-09-12 13:00:00,2026-09-13 10:00:00,McDonald's,-2890.00,0.00,HUF,COMPLETED,-14260.56", E, "dining"),
            r("CARD_PAYMENT,Current,2026-09-12 20:00:00,2026-09-13 10:00:00,BURGERKING 0123,-3190.00,0.00,HUF,COMPLETED,-17450.56", E, "dining"),
            r("ATM,Current,2026-09-13 10:00:00,2026-09-13 10:00:00,Cash withdrawal at OTP ATM Budapest,-20000.00,0.00,HUF,COMPLETED,-37450.56", T, "cash"),
            r("FEE,Current,2026-09-14 00:00:00,2026-09-14 00:00:00,Premium plan fee,-3990.00,0.00,HUF,COMPLETED,-41440.56", E, "fees"),
            r("CARD_REFUND,Current,2026-09-14 12:00:00,2026-09-15 10:00:00,Decathlon,6990.00,0.00,HUF,COMPLETED,-34450.56", I, "shopping"),
            r("INTEREST,Savings,2026-09-15 00:00:00,2026-09-15 00:00:00,Interest earned - Savings,123.45,0.00,HUF,COMPLETED,123.45", I, "interest"),
            r("CARD_PAYMENT,Current,2026-09-15 07:30:00,2026-09-16 10:00:00,OMV 4521 BUDAPEST,-18000.00,0.00,HUF,COMPLETED,-52450.56", E, "transport"),
            r("CARD_PAYMENT,Current,2026-09-16 09:00:00,2026-09-17 10:00:00,Lipóti Pékség,-1450.00,0.00,HUF,COMPLETED,-53900.56", E, "food"),
            r("CARD_PAYMENT,Current,2026-09-16 21:00:00,2026-09-17 10:00:00,Kis Kakas Söröző,-5400.00,0.00,HUF,COMPLETED,-59300.56", E, "dining"),
            r("CARD_PAYMENT,Current,2026-09-17 10:00:00,,Lidl,-1000.00,0.00,HUF,REVERTED,", E, "skip"),
            r("CARD_PAYMENT,Current,2026-09-18 10:00:00,2026-09-19 10:00:00,Szép Virág Kft.,-3500.00,0.00,HUF,COMPLETED,-62800.56", E, "other")
        )
    )

    /** OTP style: semicolon, Hungarian headers, a transaction type column, partner and remark columns. */
    val otp = Statement(
        "OTP",
        "Számlaszám;Tranzakció típusa;Könyvelés dátuma;Értéknap;Összeg;Pénznem;Partner neve;Partner számlaszáma;Közlemény",
        listOf(
            r("11773000-00000000;Vásárlás kártyával;2026.09.03.;2026.09.03.;-12 345;HUF;;;LIDL 0123 BUDAPEST HU 2026.09.02 *1234", E, "food"),
            r("11773000-00000000;Átutalás;2026.09.03.;2026.09.03.;-150 000;HUF;Bérbeadó Minta;11111111-22222222;Bérleti díj szeptember", E, "housing"),
            r("11773000-00000000;Jóváírás;2026.09.05.;2026.09.05.;650 000;HUF;Példa Munkáltató Kft.;33333333-44444444;Munkabér 2026/09", I, "income"),
            r("11773000-00000000;Készpénzfelvétel;2026.09.06.;2026.09.06.;-30 000;HUF;;;OTP ATM 1234 Budapest", T, "cash"),
            r("11773000-00000000;Számlavezetési díj;2026.09.30.;2026.09.30.;-1 290;HUF;;;", E, "fees"),
            r("11773000-00000000;Kamatjóváírás;2026.09.30.;2026.09.30.;12;HUF;;;", I, "interest"),
            r("11773000-00000000;Saját számlák közötti átutalás;2026.09.07.;2026.09.07.;-100 000;HUF;Minta Gábor;11773000-99999999;", T, "transfers"),
            r("11773000-00000000;Átvezetés;2026.09.08.;2026.09.08.;-50 000;HUF;;;Megtakarítási számlára", T, "transfers"),
            r("11773000-00000000;Csoportos beszedés;2026.09.10.;2026.09.10.;-12 990;HUF;MVM Next Energiakereskedelmi Zrt.;;Áramszámla", E, "housing"),
            r("11773000-00000000;Vásárlás kártyával;2026.09.11.;2026.09.11.;-8 500;HUF;;;MOL TÖLTŐÁLLOMÁS 123 SZEGED", E, "transport"),
            r("11773000-00000000;Átutalás;2026.09.12.;2026.09.12.;-25 000;HUF;Példa Anna;55555555-66666666;Kölcsön", P, "personal_transfers"),
            r("11773000-00000000;Vásárlás kártyával;2026.09.12.;2026.09.12.;-3 450;HUF;;;DM DROGERIE MARKT 0456", E, "health"),
            r("11773000-00000000;Vásárlás kártyával;2026.09.13.;2026.09.13.;-2 890;HUF;;;BENU GYÓGYSZERTÁR", E, "health"),
            r("11773000-00000000;Vásárlás kártyával;2026.09.13.;2026.09.13.;-1 450;HUF;;;BKK AUTOMATA", E, "transport"),
            r("11773000-00000000;Csoportos beszedés;2026.09.15.;2026.09.15.;-8 990;HUF;Magyar Telekom Nyrt.;;", E, "housing"),
            r("11773000-00000000;Átutalás;2026.09.16.;2026.09.16.;-57 000;HUF;Magyar Államkincstár;10032000-00000000;", E, "taxes"),
            r("11773000-00000000;Jóváírás;2026.09.17.;2026.09.17.;20 000;HUF;Magyar Államkincstár;10032000-00000000;Családi pótlék", I, "taxes"),
            r("11773000-00000000;Átutalás;2026.09.18.;2026.09.18.;-35 000;HUF;NAV;10032000-01076019;SZJA befizetés", E, "taxes"),
            r("11773000-00000000;Vásárlás kártyával;2026.09.19.;2026.09.19.;-4 200;HUF;;;Bortársaság 012", E, "other")
        )
    )

    /** Erste style: one description column with uppercase card noise and a type column. */
    val erste = Statement(
        "Erste",
        "Dátum;Típus;Leírás;Összeg;Pénznem",
        listOf(
            r("2026-09-02;Kártyás vásárlás;ERSTE KARTYAS VASARLAS SPAR 3311 DEBRECEN;-3 210;HUF", E, "food"),
            r("2026-09-03;Átváltás;Átváltás EUR;-40 000;HUF", T, "transfers"),
            r("2026-09-03;Átvezetés;ÁTVEZETÉS SAJÁT SZÁMLÁRA;-20 000;HUF", T, "transfers"),
            r("2026-09-04;Kártyás vásárlás;INTERSPAR 0815 BUDAPEST;-15 600;HUF", E, "food"),
            r("2026-09-05;Kártyás vásárlás;TESCO HIPERMARKET 41051;-9 870;HUF", E, "food"),
            r("2026-09-05;Kártyás vásárlás;ROSSMANN 245;-2 340;HUF", E, "health"),
            r("2026-09-06;Kártyás vásárlás;FOODORA*ORDER 88123;-5 990;HUF", E, "dining"),
            r("2026-09-07;Kártyás vásárlás;SHELL 1234 GYOR;-21 000;HUF", E, "transport"),
            r("2026-09-08;Csoportos beszedés;DIJNET FOTAV ZRT;-14 500;HUF", E, "housing"),
            r("2026-09-09;Csoportos beszedés;YETTEL HUNGARY ZRT;-6 990;HUF", E, "housing"),
            r("2026-09-10;Kártyás vásárlás;SPOTIFY P1234ABC;-1 990;HUF", E, "subscriptions"),
            r("2026-09-10;Kártyás vásárlás;HBO MAX;-2 490;HUF", E, "subscriptions"),
            r("2026-09-11;Kártyás vásárlás;CINEMA CITY ALLEE;-6 400;HUF", E, "entertainment"),
            r("2026-09-12;Kártyás vásárlás;DECATHLON BUDAORS;-12 990;HUF", E, "shopping"),
            r("2026-09-13;Kártyás vásárlás;MEDIA MARKT 0123;-49 990;HUF", E, "electronics"),
            r("2026-09-14;Kártyás vásárlás;ALZA.HU;-8 990;HUF", E, "electronics"),
            r("2026-09-15;Kártyás vásárlás;CARD PAYMENT TO AUCHAN SOROKSAR HU;-11 230;HUF", E, "food"),
            r("2026-09-16;Kártyás vásárlás;MÁV-START JEGYAUTOMATA;-2 950;HUF", E, "transport"),
            r("2026-09-17;Kártyás vásárlás;PATIKA PLUSZ 12;-3 100;HUF", E, "health"),
            r("2026-09-18;Kártyás vásárlás;ZÖLDSÉGES BT;-1 800;HUF", E, "food")
        )
    )

    /** MBH style: separate debit and credit columns, a "Megnevezés" column with a prefix. */
    val mbh = Statement(
        "MBH",
        "Dátum;Megnevezés;Terhelés;Jóváírás;Pénznem;Közlemény",
        listOf(
            r("2026.09.02.;VÁSÁRLÁS POS: ALDI 123;4 560;;HUF;", E, "food"),
            r("2026.09.05.;Jóváírás: Példa Kft.;;580 000;HUF;munkabér szeptember", I, "income"),
            r("2026.09.06.;ÁTUTALÁS: Minta Gábor;15 000;;HUF;vacsora", P, "personal_transfers"),
            r("2026.09.07.;Kártyadíj;2 500;;HUF;", E, "fees"),
            r("2026.09.08.;VÁSÁRLÁS POS: PENNY MARKET 2211;3 120;;HUF;", E, "food"),
            r("2026.09.09.;VÁSÁRLÁS POS: OMV 77 BUDAPEST;16 000;;HUF;", E, "transport"),
            r("2026.09.10.;Készpénzfelvétel ATM;20 000;;HUF;", T, "cash"),
            r("2026.09.11.;Azonnali átutalás: Példa Anna;8 000;;HUF;születésnap", P, "personal_transfers"),
            r("2026.09.12.;VÁSÁRLÁS POS: KFC 044;3 590;;HUF;", E, "dining"),
            r("2026.09.13.;Hitelkamat;1 870;;HUF;", E, "interest"),
            r("2026.09.14.;Átvezetés saját számlák között;30 000;;HUF;", T, "transfers"),
            r("2026.09.15.;VÁSÁRLÁS POS: GLOBUS KFT;2 200;;HUF;", E, "other")
        )
    )

    /** Wise style: ID prefixes encode the type, day-first dates with dashes, English descriptions. */
    val wise = Statement(
        "Wise",
        "TransferWise ID,Date,Amount,Currency,Description,Payment Reference,Running Balance,Exchange From,Exchange To,Exchange Rate,Payer Name,Payee Name,Payee Account Number,Merchant,Card Last Four Digits,Card Holder Full Name,Attachment,Note,Total fees",
        listOf(
            r("CARD-1001,15-09-2026,-12.50,EUR,Card transaction of 12.50 EUR issued by Lidl Wien,,500.00,,,,,,,Lidl Wien,1234,Test Holder,,,0.00", E, "food"),
            r("TRANSFER-1002,16-09-2026,-100.00,EUR,Sent money to Minta Gabor,lunch,400.00,,,,,Minta Gabor,HU00,,,,,,0.00", P, "personal_transfers"),
            r("TRANSFER-1003,17-09-2026,60.00,EUR,Received money from Pelda Anna with reference,rent share,460.00,,,,Pelda Anna,,,,,,,,0.00", P, "personal_transfers"),
            r("BALANCE-1004,18-09-2026,-100.00,EUR,Converted 100.00 EUR to 39000.00 HUF,,360.00,EUR,HUF,390.00,,,,,,,,,0.41", T, "transfers"),
            r("BALANCE-1005,18-09-2026,39000.00,HUF,Converted 100.00 EUR to 39000.00 HUF,,39000.00,EUR,HUF,390.00,,,,,,,,,0.00", T, "transfers"),
            r("DEPOSIT-1006,19-09-2026,200.00,EUR,Topped up balance,,560.00,,,,,,,,,,,,0.00", T, "transfers"),
            r("CARD-1007,20-09-2026,-9.99,EUR,Card transaction of 9.99 EUR issued by Spotify AB,,550.01,,,,,,,Spotify AB,1234,Test Holder,,,0.00", E, "subscriptions"),
            r("CARD-1008,21-09-2026,-45.00,EUR,Card transaction of 45.00 EUR issued by Ryanair,,505.01,,,,,,,Ryanair,1234,Test Holder,,,0.00", E, "transport"),
            r("CARD-1009,22-09-2026,-18.20,EUR,Card transaction of 18.20 EUR issued by Sample Studio Ltd,,486.81,,,,,,,Sample Studio Ltd,1234,Test Holder,,,0.00", E, "other")
        )
    )

    val all = listOf(revolut, otp, erste, mbh, wise)

    /**
     * Holdout set, written after the schema-6 keyword lists were fixed and never used to tune
     * them. It estimates how the rules do on merchants and wording they were not written for;
     * its misses are reported, not fixed, so the estimate stays honest.
     */
    val holdout = Statement(
        "Holdout",
        "Dátum;Közlemény;Összeg;Pénznem",
        listOf(
            r("2026.10.01.;ALDI 3012 VECSES;-6 780;HUF", E, "food"),
            r("2026.10.01.;Müller Drogerie 0412;-4 990;HUF", E, "health"),
            r("2026.10.02.;Douglas Parfüméria;-18 900;HUF", E, "shopping"),
            r("2026.10.02.;Szamos Cukrászda;-2 400;HUF", E, "dining"),
            r("2026.10.03.;Costa Coffee Allee;-1 850;HUF", E, "dining"),
            r("2026.10.03.;Vapiano Budapest;-7 600;HUF", E, "dining"),
            r("2026.10.04.;Jégbüfé;-1 200;HUF", E, "dining"),
            r("2026.10.04.;GOOGLE *Google One;-999;HUF", E, "subscriptions"),
            r("2026.10.05.;APPLE.COM/BILL;-1 490;HUF", E, "subscriptions"),
            r("2026.10.05.;Airbnb * HMABCDEF;-45 000;HUF", E, "other"),
            r("2026.10.06.;BOLT.EU/O/2310061234;-2 650;HUF", E, "transport"),
            r("2026.10.06.;LIME*RIDE 4KX2;-890;HUF", E, "transport"),
            r("2026.10.07.;Parkl parkolás;-600;HUF", E, "transport"),
            r("2026.10.07.;Tesla Supercharger HU;-5 400;HUF", E, "transport"),
            r("2026.10.08.;Allianz Hungária Biztosító;-9 870;HUF", E, "other"),
            r("2026.10.08.;Hitelkártya törlesztés;-80 000;HUF", T, "transfers"),
            r("2026.10.09.;Bankkártya éves díj;-4 500;HUF", E, "fees"),
            r("2026.10.09.;NKM Áramszolgáltató;-11 200;HUF", E, "housing"),
            r("2026.10.10.;Fővárosi Vízművek;-6 300;HUF", E, "housing"),
            r("2026.10.10.;Magyar Posta;-1 150;HUF", E, "other"),
            r("2026.10.11.;Kimenő átutalás Kovács Minta Éva;-15 000;HUF", P, "personal_transfers"),
            r("2026.10.11.;Bejövő azonnali átutalás Teszt Bence;12 000;HUF", P, "personal_transfers"),
            r("2026.10.12.;To Minta Gábor;-7 000;HUF", P, "personal_transfers"),
            r("2026.10.12.;Exchanged to USD;-36 500;HUF", T, "transfers"),
            r("2026.10.13.;Apple Pay top-up by *5678;30 000;HUF", T, "transfers"),
            r("2026.10.13.;Fressnapf 117;-8 990;HUF", E, "other"),
            r("2026.10.14.;H&M 0912;-12 990;HUF", E, "shopping"),
            r("2026.10.14.;iStyle WestEnd;-24 990;HUF", E, "electronics"),
            r("2026.10.15.;Edenred SZÉP kártya feltöltés;40 000;HUF", I, "income"),
            r("2026.10.15.;Visszatérítés Decathlon;3 990;HUF", I, "shopping"),
            r("2026.10.16.;PlayStation Network;-5 990;HUF", E, "subscriptions"),
            r("2026.10.16.;Wolt Market;-9 450;HUF", E, "food"),
            r("2026.10.17.;Lakáshitel törlesztés;-120 000;HUF", E, "housing"),
            r("2026.10.17.;Generali Biztosító;-6 200;HUF", E, "other"),
            r("2026.10.18.;Cashback;450;HUF", I, "income"),
            r("2026.10.18.;Magyar Telekom Mobil;-7 990;HUF", E, "housing")
        )
    )
}
