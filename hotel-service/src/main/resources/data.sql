insert into hotels (name, address) values ('Grand Hotel', 'Moscow, Tverskaya 1');
insert into hotels (name, address) values ('Sea View', 'Sochi, Kurortny 10');
insert into hotels (name, address) values ('City Inn', 'Kazan, Baumana 5');

insert into rooms (hotel_id, number, available, times_booked) values (1, '101', true, 3);
insert into rooms (hotel_id, number, available, times_booked) values (1, '102', true, 0);
insert into rooms (hotel_id, number, available, times_booked) values (1, '103', true, 1);
insert into rooms (hotel_id, number, available, times_booked) values (1, '104', false, 0);
insert into rooms (hotel_id, number, available, times_booked) values (2, '201', true, 5);
insert into rooms (hotel_id, number, available, times_booked) values (2, '202', true, 0);
insert into rooms (hotel_id, number, available, times_booked) values (3, '301', true, 2);
insert into rooms (hotel_id, number, available, times_booked) values (3, '302', true, 2);
