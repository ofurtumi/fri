## Videos

I want to be able to put videos into the image carousel, they need to be transcoded down to a lower resolution so we don't blow up the github blob max size.
We can scale them down to 480 or even 270p just to get the old timey digital camera effect.

This will affect both the android app and the webapp

## Map routes

We talked about being able to draw routes on the map based on the GPS coordinates we take with the app, now i have a bunch of points from my weekend trip so i would like us to implement that feature.
For this we also need to be able to define a start point for each trip which will act as a point on the map though it wont connect to a post neccessarily.

This also brings me to the next and final feature

## Grouping posts into trips

Since i took a trip now that is not the trip i wanted to create the app for.
I would like to be able to create groups in the app, these groups would hold posts, stats and a route map.
I would like to see my posts in the app grouped by trip and be able to update them (if possible with the current git ipmlementation)

On the site, i would like to be able to select trips from a list of trips in the sidebar, and on mobile have that selection menu as a slideout menu.
When a trip is selected we should only show the posts that belong to that trip only the stats belonging to that trip and the route map for that trip.

The gallery should be trip agnostic
